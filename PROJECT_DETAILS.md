# AI Revenue Recovery - Project Details

This document provides a comprehensive overview of the **AI Revenue Recovery** project. It is designed to be shared with an LLM for code review, architecture evaluation, and general understanding of the system's capabilities.

## 1. Project Overview

**AI Revenue Recovery** is a robust payment orchestration and recovery system. It is designed to intelligently handle payment failures, monitor bank health in real-time, recover lost revenue through "Promises to Pay" and conversational AI (WhatsApp), and gracefully manage subscriptions. 

A core feature of the system is its ability to "self-heal" ambiguous payment states by verifying ground truth with the payment gateway (Razorpay) and dynamically responding to degraded bank performance (Bank Health Monitoring).

## 2. Technology Stack

### Backend
- **Framework**: Spring Boot (v4.1.1) / Java 17
- **Database ORM**: Spring Data JPA with MySQL Connector
- **Payment Gateway Integration**: Razorpay Java SDK (v1.4.6)
- **AI Integration**: Spring AI (`spring-ai-starter-model-google-genai`) for processing conversational AI interactions and generating summaries.
- **Other**: Lombok for boilerplate reduction, Spring Session JDBC.

### Frontend
- **Framework**: React.js (Vite environment assumed based on structure)
- **Styling**: Tailwind CSS (complex utility classes for dynamic styling, glassmorphism, and gradients)
- **HTTP Client**: Axios (for API communication)
- **Integration**: Razorpay Checkout SDK via client-side script.

## 3. Core Architecture & Domain Entities

The backend is structured in a standard Spring Boot MVC pattern (`controller`, `service`, `repository`, `entity`, `job`).

### Key Entities (`/entity`)
- **`PaymentAttempt`**: The central entity recording every payment transaction. It tracks properties like `razorpayOrderId`, `amount`, `customerBank`, `paymentMethod`, and most importantly, the `status` (CREATED, AUTHORIZED, CAPTURED, AMBIGUOUS, FAILED).
- **`BankHealthSnapshot`**: Records the success rate of various banks over specific time windows. It includes `successRate`, `baselineSuccessRate`, a boolean `isDegraded`, and an `aiSummary` to provide context when a bank is failing.
- **`PromiseToPay`**: Represents a commitment by a customer to complete a failed transaction at a future date.
- **`Subscription` & `SubscriptionPaymentAttempt`**: Manages recurring billing schedules and the execution of those payments.
- **`AuditLog`**: System-level event tracking for transparency and debugging (e.g., when a payment self-heals or a bank goes down).
- **`Customer`**: Basic user profile information.
- **`ChatMemoryEntity`**: Likely used by Spring AI to maintain conversation history for conversational WhatsApp recovery.

## 4. Backend Components

### Key Controllers (`/controller`)
- **`PaymentController`**: Exposes APIs for initiating payments, fetching the latest bank health, and manually triggering the "resolution" of ambiguous payments (self-healing).
- **`WhatsappWebhookController`**: Receives incoming webhook events from WhatsApp. This acts as the entry point for conversational recovery (e.g., messaging a user whose payment failed and negotiating a Promise to Pay).
- **`BankSimulatorController`**: A mocked endpoint used for testing and demo purposes to artificially simulate bank approvals, rejections, and network drops.
- **`PromiseToPayController` & `SubscriptionController`**: Standard CRUD and operational endpoints for managing their respective domain objects.

### Key Services (`/service`)
- **`PaymentService`**: Houses the core business logic for integrating with Razorpay (creating orders, verifying signatures). It is also responsible for aggregating bank health snapshots and filtering out simulated entities (like the "Bank Simulator").
- **`WhatsappInteractionService`**: Integrates with Spring AI to process natural language replies from customers over WhatsApp and map them into system actions (like creating a `PromiseToPay`).
- **`BankSimulatorService`**: Handles the artificial queuing and processing of simulated payment attempts.

### Scheduled Jobs (`/job`)
- **`BankHealthJob`**: Periodically aggregates payment success/failure metrics to generate new `BankHealthSnapshot` records. This powers the real-time grid on the frontend.

## 5. Frontend Architecture

The frontend is a single-page React application that serves as a dashboard/demo environment.

### Main Components (`/components`)
- **`App.jsx`**: The root component that orchestrates state management (polling APIs every 3 seconds) and renders the layout.
- **`BankHealthGrid.jsx`**: A visually rich dashboard component that displays the real-time success rates of different banks. It highlights banks in a "CRITICAL DIP" state, shows the absolute dip compared to the baseline, and surfaces AI-generated summaries of the outage.
- **`PaymentStatusCard.jsx`**: Displays the lifecycle of the most recent payment attempt (Attempt ID, Order ID, Bank, Amount, Status). It provides a "Resolve Status (Self-Heal)" action button for ambiguous states.
- **`CheckoutForm.jsx`**: A form to simulate a customer checkout. Includes toggles to intentionally "simulate drop" (create ambiguous states).
- **`PromisesAndSubscriptionsCard.jsx`**: UI for managing deferred payments and recurring plans.
- **`AuditLogFeed.jsx` & `RecoveredRevenueCard.jsx`**: Dashboard widgets showing system activity and total revenue saved by the recovery mechanisms.

### API Layer (`/services/api.js`)
Centralized Axios configuration pointing to the Spring Boot backend (`http://localhost:8080`), exporting functions for all major actions (`initiatePaymentApi`, `resolvePaymentApi`, `fetchBankHealthApi`, etc.).

## 6. Highlighted Workflows

1. **Payment Initiation**: 
   - User submits `CheckoutForm`.
   - Backend `PaymentService` creates a Razorpay Order and a `PaymentAttempt` (Status: CREATED).
   - Frontend opens Razorpay Checkout.
2. **Ambiguous State & Self-Healing**:
   - If a payment drops (simulated or real network failure), the status remains CREATED or AMBIGUOUS.
   - The user can click "Resolve Status" on the `PaymentStatusCard`.
   - Backend queries Razorpay for the ground truth and updates the status to CAPTURED or FAILED accordingly.
3. **Conversational Recovery**:
   - A payment fails.
   - The system theoretically reaches out via WhatsApp.
   - The user replies (e.g., "I will pay tomorrow").
   - `WhatsappWebhookController` receives the message, Spring AI parses the intent, and a `PromiseToPay` is registered.
4. **Bank Health Degradation**:
   - `BankHealthJob` detects a spike in failures for "HDFC UPI".
   - It flags the snapshot as `isDegraded = true`.
   - The frontend `BankHealthGrid` immediately reflects this with red styling and an AI summary, potentially disabling or warning against that payment method in the checkout form.
