# AI-Powered Document & Multimedia Q&A

A full-stack web application where users can upload PDFs, audio, and video files, generate summaries, ask grounded questions, search topics, and jump to relevant timestamps.

## Live Demo

🔗 [your-railway-url]

## Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Data JPA, MySQL, Apache PDFBox
- **Frontend:** React 18, Vite
- **AI Provider:** Groq via OpenAI-compatible API
- **Chat Model:** `llama-3.3-70b-versatile`
- **Transcription Model:** `whisper-large-v3`
- **Infrastructure:** Docker, Docker Compose, GitHub Actions

## Features

- Upload PDF, audio, and video files
- Extract text from PDFs
- Transcribe audio and video into timestamped segments
- Generate summaries for uploaded files
- Ask grounded questions against uploaded content
- Return relevant text or transcript segments with answers
- Search by topic and jump to matching timestamps
- Play audio/video directly from a relevant timestamp
- Preview PDF, audio, and video in the UI

## Project Structure

```text
backend/   Spring Boot REST API
frontend/  React + Vite client
```

## Environment Variables

This project uses [Groq](https://groq.com) via an OpenAI-compatible API.
Get a free API key at https://console.groq.com — takes under a minute.

| Variable | Required | Default |
|---|---|---|
| `OPENAI_API_KEY` | Yes | — |
| `OPENAI_BASE_URL` | No | `https://api.groq.com/openai/v1` |
| `OPENAI_CHAT_MODEL` | No | `llama-3.3-70b-versatile` |
| `OPENAI_TRANSCRIPTION_MODEL` | No | `whisper-large-v3` |

> The live demo is already configured. These variables are only needed for local development.

## Local Setup

### Backend

**Linux/Mac:**
```bash
export OPENAI_API_KEY="your_groq_api_key_here"
cd backend
mvn spring-boot:run
```

**Windows PowerShell:**
```powershell
$env:OPENAI_API_KEY="your_groq_api_key_here"
cd backend
mvn spring-boot:run
```

Runs at `http://localhost:8081`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs at `http://localhost:5173`

## Docker Compose

Copy `.env.example` to `.env` and fill in your Groq API key, then run:

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend | `http://localhost:8081` |
| MySQL | `localhost:3306` |

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/assets` | Upload a PDF, audio, or video file |
| `GET` | `/api/assets` | List all uploaded assets |
| `GET` | `/api/assets/{id}` | Fetch a single asset |
| `GET` | `/api/assets/{id}/media` | Stream the original file |
| `POST` | `/api/assets/{id}/chat` | Ask a grounded question |
| `POST` | `/api/assets/{id}/topics` | Find relevant segments and timestamps |

**Chat request:**
```json
{ "question": "What are the key takeaways?" }
```

**Topic request:**
```json
{ "topic": "deployment" }
```

## Testing

```bash
cd backend
mvn test
```

Coverage report is generated at `backend/target/site/jacoco/index.html`.
The build enforces a minimum of 95% coverage via JaCoCo.

```bash
cd frontend
npm run build
```

## Notes

- PDF text extraction uses Apache PDFBox.
- Topic search uses keyword ranking over extracted text and transcript segments.
- PDF matches are chunk-based; audio/video matches include timestamps for the frontend player.