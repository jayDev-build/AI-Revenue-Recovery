# AI Revenue Recovery: Issues & Resolutions Log

This document tracks the technical hurdles, edge cases, and architectural issues we encountered while building and refining the revenue recovery system, along with the precise methods used to resolve them.

## 1. Spring AI PromptTemplate Crashing on JSON
- **The Issue:** When attempting to enforce strict JSON output constraints for the Autonomous Agent, the application threw a `500 ERROR: The template string is not valid`. Spring AI's internal `PromptTemplate` engine attempts to parse any curly braces `{}` as template variables. Because our prompt contained raw JSON schemas (`{ "status": "..." }`), the parser crashed looking for variables that didn't exist.
- **The Resolution:** We bypassed the Spring AI template parser for the strict persona instructions. Instead of passing the constraints into a `PromptTemplate`, we used standard Java `String.replace()` to manually inject variables like `customer_name` and `current_date` into the raw string before sending it to the model.

## 2. PTP Payments Remaining "PENDING" Locally
- **The Issue:** During local testing, when a user clicked "Pay Now" on a Promise to Pay (PTP), completed the Razorpay modal, and returned to the UI, the PTP remained stuck in the `PENDING` state. The backend was configured to update the status via Razorpay Webhooks, but because the local environment was not running an `ngrok` tunnel, the webhooks were silently dropped by the internet.
- **The Resolution:** We decoupled the local PTP resolution from the fragile webhook flow. We created a dedicated `POST /api/promises/{promiseId}/verify` endpoint. Now, immediately after the React Razorpay modal fires its `onSuccess` callback, the frontend explicitly calls this verification endpoint to instantly and deterministically update the database, instantly clearing the UI.

## 3. Chaos Test Demo Stalling (The 15-Minute Wait)
- **The Issue:** The "Chaos Test" feature is designed to simulate a dropped network acknowledgment, saving the payment as `AMBIGUOUS`. However, to prevent race conditions, the autonomous `PaymentRecoveryCron` is programmed to ignore payments until they are older than 15 minutes. This meant a live demo of the sweeper would require sitting and waiting for 15 actual minutes.
- **The Resolution:** We implemented a "Time Travel" bypass in the `PaymentService.initiatePayment` method. When the `simulateDrop` flag is checked, the initial `PaymentAttempt` is saved with its `initiatedAt` timestamp artificially backdated by 16 minutes (`AppClock.now().minusMinutes(16)`). The 10-second cron sweeper instantly detects this as an expired payment and resolves it on its very next tick.

## 4. Duplicate Pending Subscription Charges Stacking
- **The Issue:** If a user repeatedly clicked "Pay Now" on a subscription but abandoned the checkout window, the `payment_attempt` database would infinitely stack up duplicate `PENDING` records for the exact same subscription, polluting the history.
- **The Resolution:** We updated the `BankSimulatorService` to automatically execute a cleanup step before generating a new request. Any older unresolved attempts for that specific subscription are automatically marked as `FAILED` with a `SUPERSEDED_BY_NEW_REQUEST` reason code.

## 5. N+1 Read Bottlenecks During Pending Cleanup
- **The Issue:** The initial fix for the duplicate pending charges (Issue #4) involved fetching a `List` of all pending attempts into Java memory and looping over them to save them as `FAILED`. At enterprise scale, pulling hundreds of records over the network to instantiate Java objects creates massive N+1 read/write bottlenecks and memory overhead.
- **The Resolution:** We eliminated the Java loop entirely by writing a bulk `@Modifying` JPQL query (`UPDATE PaymentAttempt p SET p.status = 'FAILED' ...`). This reduced the cleanup operation to exactly **0 reads and 1 highly-optimized write query** directly at the database level, preventing any application memory bloat.

## 6. Recovered Revenue Tracking Omissions
- **The Issue:** The overall `Customer.recovered` revenue metric was only incrementing when standard subscription payments succeeded. It was failing to track the revenue recovered explicitly from resolved Promise to Pay (PTP) interventions.
- **The Resolution:** We updated the new `/verify` endpoint to calculate the exact amount paid for the PTP. It intelligently falls back to the AI's dynamically extracted amount if the linked subscription plan is missing, and explicitly appends that value to the `customer.recovered` BigDecimal field, ensuring the dashboard metrics are 100% accurate.



Here is a complete summary of all the issues we encountered and exactly how we resolved them:

### 7. 500 Internal Server Error & StackOverflow Crashes
*   **The Issue:** When you manually added the `@OneToMany` list of `subscriptions` to the `Customer` entity, you missed the `mappedBy` parameter. Hibernate got confused and looked for a non-existent join table (`customer_subscriptions`). Worse, the bidirectional link caused an infinite loop in Java's `toString()` method, crashing the server entirely.
*   **The Fix:** I added `mappedBy = "customer"` to the mapping, and added `@ToString.Exclude` and `@EqualsAndHashCode.Exclude` to the list to prevent the infinite memory loop.

### 8. Spring AI Crashing on Multiple System Messages
*   **The Issue:** We tried to give the LLM the exact current date/time using a second `SystemMessage`. However, the underlying AI model strictly allows only one System Message per prompt, which caused Spring AI to throw a fatal exception.
*   **The Fix:** I bypassed this restriction by injecting the `[CRITICAL CONTEXT: The current server date...]` string directly into the front of the incoming `UserMessage` right before sending it to the AI.

### 9. Paid Promises Refused to Leave the Home Page
*   **The Issue:** When a user paid a "Promise to Pay" via Razorpay on the frontend, it stayed visible on the UI. The backend was returning *all* promises, even the paid ones.
*   **The Fix:** I modified `PromiseToPayController` to filter the API response so it only ever returns `PENDING` promises. Paid ones now vanish instantly upon a successful refresh.

### 10. Ghost Requests Stuck in the Bank Simulator
*   **The Issue:** If a user paid their subscription manually on the home page, the Bank Simulator had no idea. The simulator's original `PaymentAttempt` was left stuck in `PENDING` forever.
*   **The Fix:** I updated `SubscriptionService.paySubscription()`. Now, when a manual payment succeeds, the backend actively hunts down any stuck `PENDING` or `CREATED` attempts for that subscription in the simulator and forcefully flips them to `CAPTURED`.

### 11. Revenue Amount Required Manual Refreshing
*   **The Issue:** The massive "Recovered Amount" on the dashboard wouldn't update after a payment unless you manually clicked the refresh icon. The backend wasn't updating the total, and the frontend wasn't checking for it.
*   **The Fix:** I updated the backend to actively add the paid money to the `Customer`'s `recovered` column in the database. Then, I updated `App.jsx` in the frontend to quietly fetch the latest recovered amount every 3 seconds in the background.

### 12. LazyInitializationException on WhatsApp Messages
*   **The Issue:** The backend threw a database error (`no session`) when processing incoming WhatsApp messages because it tried to read `customer.getSubscriptions()` after the database connection had already closed.
*   **The Fix:** I added a simple `@Transactional` annotation to `processIncomingMessage()` in `WhatsappInteractionService`, forcing the database connection to stay open for the entire process.

### 13. AI Stripping the Time (00:00:00 Bug)
*   **The Issue:** When you said "I'll pay in 2 minutes", the AI correctly calculated the day but set the time to exactly `00:00:00`. This happened because the System Prompt strictly told the AI to format the JSON date as `"YYYY-MM-DD"`, forcing it to throw away the hours and minutes!
*   **The Fix:** I updated the System Prompt to strictly demand `"YYYY-MM-DDTHH:MM:SS"`. I also updated the backend to take that precise time and automatically update the Subscription's `nextChargeDate` so the Bank Simulator completely stops retrying until that exact minute arrives.

### 14. Missing Subscription Billing Cycles
*   **The Issue:** The frontend form only allowed creating subscriptions that renewed every few seconds; there was no way to set it for 1 Day or 30 Days.
*   **The Fix:** I updated the dropdown in `SubscriptionForm.jsx` to include `1 Day` (86400 seconds) and `30 Days` (2592000 seconds), mapping perfectly to the backend database.
