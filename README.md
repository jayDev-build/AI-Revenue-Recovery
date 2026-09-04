# AI Revenue Recovery

AI Revenue Recovery is a full-stack application built to automate and optimize the recovery of failed or pending payments. It acts as an **Autonomous Agent**, taking over the historically manual process of following up with customers. 

### Core Workflow
1. **Intelligent Engagement:** The system uses **Spring AI** to dynamically generate personalized WhatsApp messages for customers who have missed a payment. These are delivered via the **Meta WhatsApp Business API**.
2. **Promise to Pay (PTP):** The AI negotiates a "Promise to Pay" (PTP) arrangement with the customer based on strict persona instructions.
3. **Frictionless Payments:** When the customer is ready, the system provides a secure payment link generated via **Razorpay**. 
4. **Automated Reconciliation:** Upon successful payment, Razorpay webhooks instantly notify the backend to automatically mark the PTP as fulfilled and update the customer's status in the MySQL database.

### Tech Stack
- **Backend:** Spring Boot (Java 17), Spring AI, MySQL, Razorpay Java SDK.
- **Frontend:** React 19, Vite, TailwindCSS.

This guide provides step-by-step instructions on setting up the project locally, creating the necessary third-party accounts (Meta and Razorpay), and using `cloudflared` to expose your local server for webhook testing.

---

## Prerequisites

- Java 17 or higher
- Node.js (v18+) and npm/yarn
- Maven
- MySQL Database
- [Cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) installed for exposing local ports.

---

## 1. Setting up Cloudflared for Webhooks

To receive webhook events from Meta (WhatsApp) and Razorpay on your local machine, you need to expose your local backend server to the internet. We use `cloudflared` for this.

1. Install Cloudflared from the link above or via package managers (e.g., `brew install cloudflare/cloudflare/cloudflared` on macOS, or download the `.exe` for Windows).
2. Run the following command to expose your backend port (default Spring Boot port is `8080`):
   ```bash
   cloudflared tunnel --url http://localhost:8080
   ```
3. Cloudflared will generate a random forwarding URL (e.g., `https://your-random-subdomain.trycloudflare.com`). **Keep this terminal running.**
4. You will use this base URL in the Meta and Razorpay webhook configurations below.

---

## 2. Setting up Razorpay (Test Mode)

Razorpay is used to generate payment links and process transactions.

### Creating a Test Account
1. Go to [Razorpay](https://razorpay.com/) and sign up for an account.
2. Once logged in, ensure you are in **Test Mode** (toggle is usually at the top right or left sidebar of the dashboard).
3. Navigate to **Account & Settings > API Keys**.
4. Click **Generate Test Key**. You will receive a `Key Id` and a `Key Secret`. Save these in your backend `.env` file.

### Configuring Razorpay Webhooks
To get notified when a customer makes a payment:
1. Navigate to **Account & Settings > Webhooks** in the Razorpay Dashboard.
2. Click **Add New Webhook**.
3. **Webhook URL:** Enter your Cloudflared URL followed by your backend webhook endpoint (e.g., `https://<your-cloudflare-url>.trycloudflare.com/api/razorpay/webhook`).
4. **Secret:** Create a custom secret string (e.g., `my_razorpay_secret`) and save it in your backend `.env` file for signature verification.
5. **Active Events:** Select the events you want to listen to (e.g., `payment.captured`, `payment.failed`, `order.paid`).
6. Click **Create Webhook**.

---

## 3. Setting up Meta (WhatsApp Business API)

We use the WhatsApp Business API to send AI-generated messages to customers regarding their pending payments.

### Creating a Meta App
1. Go to the [Meta for Developers](https://developers.facebook.com/) portal and log in.
2. Click on **My Apps** -> **Create App**.
3. Select **Other** -> **Next** -> **Business** -> **Next**.
4. Name your app (e.g., `AI Revenue Recovery`) and link it to a Business Account (or create a new one). Click **Create App**.

### Configuring WhatsApp
1. In the App Dashboard, scroll down to **Add products to your app** and set up **WhatsApp**.
2. Navigate to **WhatsApp > API Setup** in the left sidebar.
3. Note down the **Temporary access token**, **Phone number ID**, and **WhatsApp Business Account ID**. These will go into your backend `.env` file.
   > **Note:** Temporary tokens expire every 24 hours. For production, you will need to generate a permanent System User token.
4. To test, add your personal WhatsApp number in the **To** field and verify it.

### Configuring Meta Webhooks
To receive messages from customers:
1. Navigate to **WhatsApp > Configuration** in the left sidebar.
2. Click **Edit** under the Webhook section.
3. **Callback URL:** Enter your Cloudflared URL (e.g., `https://<your-cloudflare-url>.trycloudflare.com/api/meta/webhook`).
4. **Verify Token:** Enter a custom string (e.g., `my_meta_verify_token`) and save it in your `.env` file. The backend needs to echo this token during the initial verification request.
5. Click **Verify and Save**.
6. Once saved, click **Manage** under Webhook fields and subscribe to the `messages` event.

---

## 4. Local Environment Setup

### Backend Setup
1. Navigate to the `backend` directory:
   ```bash
   cd backend
   ```
2. Create a `.env` file in the `backend` directory (or configure `application.properties`/`application.yml`) based on the following template:
   ```env
   # Database
   DB_URL=jdbc:mysql://localhost:3306/revenue_recovery
   DB_USERNAME=root
   DB_PASSWORD=your_mysql_password

   # Razorpay
   RAZORPAY_KEY_ID=rzp_test_xxxxxxx
   RAZORPAY_KEY_SECRET=your_razorpay_secret
   RAZORPAY_WEBHOOK_SECRET=my_razorpay_secret

   # Meta / WhatsApp
   META_ACCESS_TOKEN=your_meta_access_token
   META_PHONE_NUMBER_ID=your_phone_number_id
   META_WEBHOOK_VERIFY_TOKEN=my_meta_verify_token

   # AI / LLM configuration (e.g., OpenAI or Google GenAI API key used by Spring AI)
   SPRING_AI_API_KEY=your_ai_api_key
   ```
3. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```

### Frontend Setup
1. Navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   # or
   yarn install
   ```
3. Create a `.env` file (if required) to set the API Base URL:
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   ```
4. Start the Vite development server:
   ```bash
   npm run dev
   # or
   yarn dev
   ```

---

## 5. Running the Complete Flow locally
1. Ensure MySQL is running and the database is created.
2. Start the Backend server (`mvnw spring-boot:run`).
3. Start the Frontend server (`npm run dev`).
4. Start `cloudflared` to expose port 8080 and ensure the URL matches your Meta and Razorpay webhook configurations.
5. Interact with the frontend to trigger a payment link generation.
6. Check your WhatsApp for the AI-generated message.
7. Complete a test payment via Razorpay. The Razorpay webhook will notify your backend, and the database will be updated automatically!
