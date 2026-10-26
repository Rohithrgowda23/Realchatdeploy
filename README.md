# RealChat — Real-Time Messaging Application

RealChat is a full-stack messaging application built with **Java, Spring Boot, React, and PostgreSQL**. It provides direct and group conversations, photo sharing, secure account management, and AI-powered features for translating messages and summarizing conversations.

## Live Demo

**Frontend:** https://realchatdeploy.vercel.app/

## Features

### Messaging
- Real-time messaging between users
- Direct conversations and group chats
- Photo sharing and attachment support
- Message deletion with **Delete for Me** and **Delete for Everyone**
- Message replies and forwarding support

### Authentication and Account Management
- Secure authentication and authorization
- User profile management
- Session management
- Connected account support
- Permanent account deletion

### AI-Powered Features
- **AI Message Translation:** Translate text messages using an AI provider.
- **AI Conversation Summaries:** Generate summaries of selected messages within a conversation.
- **Ollama Cloud Integration:** Connect to an OpenAI-compatible AI API through the Spring Boot backend.

### User Experience
- Responsive React interface
- Dark and light themes
- User settings and profile customization
- Blocked-user management

## Technology Stack

| Layer | Technologies |
|---|---|
| Backend | Java 17, Spring Boot |
| Security | Spring Security, JWT-based authentication |
| Frontend | React.js, Vite, JavaScript |
| Database | PostgreSQL |
| Database migrations | Flyway |
| APIs | REST APIs |
| Real-time communication | WebSocket |
| AI integration | Ollama Cloud, OpenAI-compatible API |
| Backend deployment | Render |
| Frontend deployment | Vercel |
| Build tools | Maven, npm |

## Architecture

```text
React + Vite Frontend
        |
        | REST API / WebSocket
        v
Spring Boot Backend
        |
        +---- PostgreSQL
        |
        +---- Flyway Migrations
        |
        +---- Ollama Cloud API
              |
              +---- Message Translation
              +---- Conversation Summaries
```

## Getting Started

### Prerequisites

Install the following tools:

- Java 17
- Maven
- Node.js and npm
- PostgreSQL

### 1. Clone the repository

```bash
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd realchatdeploy
```

Replace `<YOUR_GITHUB_REPOSITORY_URL>` with your actual repository URL.

### 2. Configure the backend

Configure the database connection and required application properties using your local environment.

Example environment variables for AI integration:

```properties
AI_MODE=openai
AI_BASE_URL=https://ollama.com/v1
AI_API_KEY=<YOUR_OLLAMA_API_KEY>
AI_MODEL=<SUPPORTED_OLLAMA_CLOUD_MODEL>
```

Use a model ID supported by your Ollama Cloud account. Keep API keys and database credentials out of source control.

### 3. Start the backend

From the backend directory, run:

```bash
mvn spring-boot:run
```

Ensure your PostgreSQL database is running and the database configuration is correct before starting the application.

### 4. Start the frontend

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL displayed by Vite in your terminal.

## AI Integration

RealChat integrates with an OpenAI-compatible API endpoint provided by Ollama Cloud.

**Message translation**
1. Open a conversation containing text messages.
2. Open the message action bar.
3. Select the translation option.
4. Choose the target language and request the translation.

**Conversation summarization**
1. Open a conversation.
2. Open the summarization feature.
3. Select a supported message range.
4. Generate a summary.

Selected message text, sender names, timestamps, and other data described by the application's AI interface may be sent to the configured AI provider. Review the privacy implications before using the feature with sensitive conversations.

## Deployment

- **Frontend:** Vercel
- **Backend:** Render
- **Database:** PostgreSQL

Configure production environment variables in the hosting dashboard. Never commit production secrets, API keys, passwords, or private connection strings to GitHub.

## Future Improvements

- AI-assisted reply suggestions
- Enhanced media management
- Improved message search
- Additional privacy and account-security controls
- Expanded automated testing and monitoring

## Author

**Rohith R G Gowda**

Java Full Stack Developer

- Portfolio: https://rohithgowda.netlify.app/
- LinkedIn: https://linkedin.com/in/rohithrgowda
- GitHub: https://github.com/Rohithrgowda23

## License

Choose and add a license file before distributing the project for reuse.
