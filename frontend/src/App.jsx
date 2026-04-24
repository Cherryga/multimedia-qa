import { useEffect, useRef, useState } from 'react'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

const emptyChat = { question: '', answer: '', relevantSegments: [] }

// Keep long transcript snippets short inside the cards.
function truncateSegmentText(text, maxChars = 220) {
  if (!text) return ''
  const cleaned = text.replace(/\s+/g, ' ').trim()
  if (cleaned.length <= maxChars) return cleaned
  return cleaned.slice(0, maxChars).replace(/\s+\S*$/, '') + '…'
}

// Split the answer into simple paragraphs and lists.
function parseAnswerLines(text) {
  if (!text) return []
  return text
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const isBullet = /^[●•\-–—*]/.test(line)
      const isNumbered = /^\d+[.)]\s/.test(line)
      const clean = isBullet
        ? line.replace(/^[●•\-–—*]\s*/, '').trim()
        : isNumbered
        ? line.replace(/^\d+[.)]\s*/, '').trim()
        : line
      return { isBullet: isBullet && !isNumbered, isNumbered, text: clean }
    })
}

function FormattedAnswer({ text, placeholder }) {
  if (!text) return <p className="answer-placeholder">{placeholder}</p>
  const lines = parseAnswerLines(text)
  const groups = []
  let bullets = [], numbered = []
  const flushB = () => { if (bullets.length) { groups.push({ type: 'ul', items: [...bullets] }); bullets = [] } }
  const flushN = () => { if (numbered.length) { groups.push({ type: 'ol', items: [...numbered] }); numbered = [] } }
  for (const line of lines) {
    if (line.isBullet) { flushN(); bullets.push(line.text) }
    else if (line.isNumbered) { flushB(); numbered.push(line.text) }
    else { flushB(); flushN(); groups.push({ type: 'p', text: line.text }) }
  }
  flushB(); flushN()
  return (
    <div className="formatted-answer">
      {groups.map((g, i) =>
        g.type === 'ul' ? <ul key={i} className="answer-list">{g.items.map((it, j) => <li key={j}>{it}</li>)}</ul>
        : g.type === 'ol' ? <ol key={i} className="answer-list">{g.items.map((it, j) => <li key={j}>{it}</li>)}</ol>
        : <p key={i} className="answer-para">{g.text}</p>
      )}
    </div>
  )
}

function App() {
  const [assets, setAssets] = useState([])
  const [selectedAssetId, setSelectedAssetId] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [question, setQuestion] = useState('')
  const [chatResult, setChatResult] = useState(emptyChat)
  const [topic, setTopic] = useState('')
  const [topicMatches, setTopicMatches] = useState([])
  const [status, setStatus] = useState('Ready')
  const [config, setConfig] = useState({ openAiConfigured: true })
  const [requestError, setRequestError] = useState('')
  const [textExpanded, setTextExpanded] = useState(false)
  const [copied, setCopied] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const mediaRef = useRef(null)
  const fileInputRef = useRef(null)

  const SUPPORTED_FILE_TYPES = ['.pdf', '.mp3', '.wav', '.mp4', '.mov', '.mkv', '.webm']
  const selectedAsset = assets.find((asset) => asset.id === selectedAssetId) || null
  const extractedText = selectedAsset?.extractedText || ''
  const showTextToggle = extractedText.length > 1400

  function isSupportedFile(file) {
    const lowerName = file.name.toLowerCase()
    if (SUPPORTED_FILE_TYPES.some((ext) => lowerName.endsWith(ext))) return true
    return file.type.startsWith('audio/') || file.type.startsWith('video/')
  }

  useEffect(() => { loadAssets(); loadConfig() }, [])

  async function loadConfig() {
    try {
      const response = await fetch(`${API_BASE}/api/config`)
      if (!response.ok) return
      const data = await response.json()
      if (typeof data?.openAiConfigured === 'boolean') setConfig({ openAiConfigured: data.openAiConfigured })
    } catch { }
  }

  async function loadAssets() {
    try {
      setRequestError('')
      const response = await fetch(`${API_BASE}/api/assets`)
      if (!response.ok) throw new Error(`Failed to load assets (${response.status})`)
      const data = await response.json()
      setAssets(data)
      if (data.length && !selectedAssetId) setSelectedAssetId(data[0].id)
    } catch (err) {
      setRequestError(err?.message || 'Failed to load assets')
    }
  }

  async function handleFile(file) {
    if (!file) return

    if (!isSupportedFile(file)) {
      setRequestError('Unsupported file type. Please upload a PDF, audio, or video file.')
      return
    }

    // If the same file is uploaded again, just select it.
    const existing = assets.find((a) => a.originalFilename === file.name)
    if (existing) {
      setSelectedAssetId(existing.id)
      setChatResult(emptyChat)
      setTopicMatches([])
      setTextExpanded(false)
      setStatus(`"${file.name}" already uploaded — selected for you.`)
      return
    }

    const formData = new FormData()
    formData.append('file', file)
    setUploading(true)
    setRequestError('')
    setStatus(`Uploading ${file.name}…`)
    try {
      const response = await fetch(`${API_BASE}/api/assets`, {
        method: 'POST',
        body: formData,
      })
      if (!response.ok) throw new Error(`Upload failed (${response.status})`)
      const data = await response.json()
      setAssets((current) => [data, ...current])
      setSelectedAssetId(data.id)
      setChatResult(emptyChat)
      setTopicMatches([])
      setTextExpanded(false)
      setStatus(`Processed ${file.name}`)
    } catch (err) {
      setRequestError(err?.message || 'Upload failed')
      setStatus('Ready')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  function handleInputChange(event) { handleFile(event.target.files?.[0]) }

  function handleDrop(event) {
    event.preventDefault()
    setDragOver(false)
    handleFile(event.dataTransfer.files?.[0])
  }

  async function askQuestion(event) {
    event.preventDefault()
    if (!selectedAsset || !question.trim()) return
    setRequestError('')
    setStatus('Generating answer…')
    try {
      const response = await fetch(`${API_BASE}/api/assets/${selectedAsset.id}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question }),
      })
      if (!response.ok) throw new Error(`Chat failed (${response.status})`)
      const data = await response.json()
      setChatResult({ question, ...data })
      setStatus('Answer ready')
    } catch (err) {
      setRequestError(err?.message || 'Chat failed')
      setStatus('Ready')
    }
  }

  async function findTopic(event) {
    event.preventDefault()
    if (!selectedAsset || !topic.trim()) return
    setRequestError('')
    setStatus('Finding matching timestamps…')
    try {
      const response = await fetch(`${API_BASE}/api/assets/${selectedAsset.id}/topics`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ topic }),
      })
      if (!response.ok) throw new Error(`Topic search failed (${response.status})`)
      const data = await response.json()
      setTopicMatches(data)
      setStatus(data.length ? 'Timestamp matches ready' : 'No matching timestamps found')
    } catch (err) {
      setRequestError(err?.message || 'Topic search failed')
      setStatus('Ready')
    }
  }

  function playFrom(startSeconds) {
    if (!mediaRef.current) return
    mediaRef.current.currentTime = startSeconds
    mediaRef.current.play()
  }

  async function copyExtractedText() {
    if (!extractedText) return
    try {
      await navigator.clipboard.writeText(extractedText)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1200)
    } catch { }
  }

  return (
    <>
      <style>{`
        /* Upload box */
        .upload-drop-zone {
          border: 2px dashed rgba(124, 92, 255, 0.55);
          border-radius: 18px;
          padding: 28px 20px;
          text-align: center;
          cursor: pointer;
          transition: border-color .2s, background .2s, transform .15s;
          background: rgba(124, 92, 255, 0.06);
          position: relative;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 10px;
          user-select: none;
        }
        .upload-drop-zone:hover:not(.disabled),
        .upload-drop-zone.drag-over {
          border-color: rgba(124, 92, 255, 0.9);
          background: rgba(124, 92, 255, 0.13);
          transform: translateY(-2px);
        }
        .upload-drop-zone.disabled { opacity: 0.55; cursor: progress; }
        .upload-drop-zone input[type="file"] {
          position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; height: 100%;
        }
        .upload-drop-zone.disabled input { pointer-events: none; }

        .upload-icon-wrap {
          width: 52px; height: 52px;
          border-radius: 14px;
          background: linear-gradient(135deg, rgba(124,92,255,0.25), rgba(25,195,255,0.15));
          border: 1px solid rgba(124,92,255,0.3);
          display: flex; align-items: center; justify-content: center;
          font-size: 1.5rem;
          flex-shrink: 0;
        }
        .upload-main-text {
          font-size: 0.95rem;
          font-weight: 600;
          color: var(--text);
          margin: 0;
        }
        .upload-main-text span {
          color: var(--accent);
          text-decoration: underline;
          text-underline-offset: 3px;
        }
        .upload-sub-text {
          font-size: 0.8rem;
          color: var(--muted-2);
          margin: 0;
        }
        .upload-types {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
          justify-content: center;
          margin-top: 2px;
        }
        .upload-type-pill {
          font-size: 0.72rem;
          font-weight: 600;
          letter-spacing: .06em;
          padding: 3px 8px;
          border-radius: 999px;
          background: rgba(255,255,255,0.07);
          border: 1px solid rgba(255,255,255,0.14);
          color: var(--muted);
        }
        .upload-spinner {
          width: 18px; height: 18px;
          border: 2px solid rgba(124,92,255,0.3);
          border-top-color: var(--accent);
          border-radius: 50%;
          animation: spin .7s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* Answer blocks */
        .formatted-answer { display: grid; gap: 10px; }
        .answer-para { margin: 0; color: var(--muted); line-height: 1.65; font-size: 0.95rem; }
        .answer-placeholder { margin: 0; color: var(--muted-2); font-style: italic; font-size: 0.95rem; }
        .answer-list { margin: 0; padding-left: 1.4em; display: grid; gap: 7px; color: var(--muted); font-size: 0.95rem; line-height: 1.6; }
        .answer-list li { padding-left: 4px; }

        /* Keep segment text compact */
        .segment-text { margin: 6px 0 0; color: var(--muted); font-size: 0.88rem; line-height: 1.55;
          display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

        /* Sidebar scrolling */
        .sidebar { height: 100vh; position: sticky; top: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 20px; }
        .asset-list { flex: 1 1 auto; min-height: 220px; overflow-y: auto; scrollbar-width: thin; scrollbar-color: rgba(255,255,255,0.12) transparent; }
        .asset-list::-webkit-scrollbar { width: 4px; }
        .asset-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.14); border-radius: 4px; }
        .status-panel { flex-shrink: 0; margin-top: 0; }

        /* Summary and transcript should line up */
        .hero-card { align-items: stretch; }
        .hero-col { display: flex; flex-direction: column; min-height: 0; }
        .hero-col .summary-text { flex: 1; overflow-y: auto; scrollbar-width: thin; scrollbar-color: rgba(255,255,255,0.12) transparent; }
        .hero-col .transcript-box { flex: 1; display: flex; flex-direction: column; max-height: none; overflow: hidden; }
        .hero-col .transcript-box .transcript-text { flex: 1; overflow-y: auto; scrollbar-width: thin; scrollbar-color: rgba(255,255,255,0.12) transparent; }
      `}</style>

      <div className="app-shell">
        {/* Left panel */}
        <aside className="sidebar">
          <div>
            <p className="eyebrow">SDE-1 Assignment</p>
            <h1>AI Document &amp; Multimedia Q&amp;A</h1>
            <p className="lede">
              Upload PDFs, audio, or video, then ask grounded questions, review summaries, and jump
              straight to the relevant timestamp.
            </p>
          </div>

          {!config.openAiConfigured && (
            <div className="banner warning">
              <strong>Groq API key not configured</strong>
              <p>Set <code>OPENAI_API_KEY</code> to enable summaries, Q&amp;A, and transcription.</p>
            </div>
          )}

          {!!requestError && (
            <div className="banner error">
              <strong>Error</strong>
              <p>{requestError}</p>
            </div>
          )}

          {/* Upload area */}
          <div
            className={`upload-drop-zone ${uploading ? 'disabled' : ''} ${dragOver ? 'drag-over' : ''}`}
            onDragOver={(e) => { e.preventDefault(); if (!uploading) setDragOver(true) }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            onClick={() => !uploading && fileInputRef.current?.click()}
          >
            <div className="upload-icon-wrap">
              {uploading ? <div className="upload-spinner" /> : <span>📄</span>}
            </div>
            <p className="upload-main-text">
              {uploading
                ? 'Processing file…'
                : <><span>Click to upload</span> or drag &amp; drop</>}
            </p>
            <p className="upload-sub-text">Max file size 200 MB</p>
            <div className="upload-types">
              {['PDF', 'MP3', 'WAV', 'MP4', 'MOV', 'MKV', 'WEBM'].map((t) => (
                <span key={t} className="upload-type-pill">{t}</span>
              ))}
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.mp3,.wav,.mp4,.mov,.mkv,.webm,audio/*,video/*"
              onChange={handleInputChange}
              disabled={uploading}
              style={{ display: 'none' }}
            />
          </div>

          <div className="asset-list-header">
            <p className="eyebrow">Uploaded assignments</p>
            <p className="asset-list-help">Select an item to review the summary, transcript, and Q&amp;A.</p>
          </div>

          <div className="asset-list">
            {assets.map((asset) => (
              <button
                key={asset.id}
                className={`asset-chip ${asset.id === selectedAssetId ? 'active' : ''}`}
                onClick={() => {
                  setSelectedAssetId(asset.id)
                  setChatResult(emptyChat)
                  setTopicMatches([])
                  setTextExpanded(false)
                }}
              >
                <span>{asset.assetType}</span>
                <strong>{asset.originalFilename}</strong>
              </button>
            ))}
            {!assets.length && (
              <div className="empty-state">
                <strong>No uploads yet</strong>
                <p>Upload a PDF, audio, or video to get started.</p>
              </div>
            )}
          </div>

          <div className="status-panel">{status}</div>
        </aside>

        {/* Main content */}
        <main className="content-grid">
          {/* Summary and extracted text */}
          <section className="hero-card">
            <div className="hero-col">
              <div className="section-header">
                <div>
                  <p className="eyebrow">Content Summary</p>
                  <h2>{selectedAsset ? selectedAsset.originalFilename : 'Select an uploaded file'}</h2>
                </div>
              </div>
              <div className="summary-text">
                <FormattedAnswer
                  text={selectedAsset?.summary}
                  placeholder="Upload a file to generate a summary."
                />
              </div>
            </div>

            <div className="hero-col">
              <div className="transcript-box">
                <div className="transcript-header">
                  <h3>Extracted text</h3>
                  <div className="transcript-actions">
                    <button type="button" className="ghost-button" onClick={copyExtractedText} disabled={!extractedText}>
                      {copied ? 'Copied' : 'Copy'}
                    </button>
                    {showTextToggle && (
                      <button type="button" className="ghost-button" onClick={() => setTextExpanded((v) => !v)}>
                        {textExpanded ? 'Collapse' : 'Expand'}
                      </button>
                    )}
                  </div>
                </div>
                <div className={`transcript-text preserve-whitespace ${textExpanded ? '' : 'transcript-clamp'}`}>
                  {extractedText || 'Transcript or PDF text will appear here.'}
                </div>
              </div>
            </div>
          </section>

          {/* Chat */}
          <section className="panel">
            <div className="section-header">
              <div>
                <p className="eyebrow">Chatbot</p>
                <h2>Ask a grounded question</h2>
              </div>
            </div>
            <form className="stack" onSubmit={askQuestion}>
              <textarea rows="4" placeholder="What are the key takeaways?" value={question} onChange={(e) => setQuestion(e.target.value)} />
              <button type="submit" className="primary-button" disabled={!selectedAsset || uploading}>Ask</button>
            </form>
            <div className="answer-card" style={{ marginTop: 18 }}>
              <h3>Answer</h3>
              <FormattedAnswer text={chatResult.answer} placeholder="Your answer will appear here." />
              {!!chatResult.relevantSegments?.length && (
                <div className="segment-list">
                  {chatResult.relevantSegments.map((seg, i) => (
                    <article key={`${seg.startSeconds}-${i}`} className="segment-card">
                      <div>
                        <strong className="time-badge">{formatTimestamp(seg.startSeconds)}</strong>
                        <p className="segment-text">{truncateSegmentText(seg.text)}</p>
                      </div>
                      {selectedAsset?.assetType !== 'PDF' && (
                        <button onClick={() => playFrom(seg.startSeconds)}>Play</button>
                      )}
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>

          {/* Topic search */}
          <section className="panel">
            <div className="section-header">
              <div>
                <p className="eyebrow">Topic Search</p>
                <h2>Find timestamps by topic</h2>
              </div>
            </div>
            <form className="stack" onSubmit={findTopic}>
              <input type="text" placeholder="Try: deployment, onboarding, pricing" value={topic} onChange={(e) => setTopic(e.target.value)} />
              <button type="submit" className="secondary-button" disabled={!selectedAsset || uploading}>Find timestamps</button>
            </form>
            <div className="segment-list">
              {topicMatches.map((seg, i) => (
                <article key={`${seg.startSeconds}-${i}`} className="segment-card">
                  <div>
                    <strong className="time-badge">{formatTimestamp(seg.startSeconds)} – {formatTimestamp(seg.endSeconds)}</strong>
                    <p className="segment-text">{truncateSegmentText(seg.text)}</p>
                  </div>
                  {selectedAsset?.assetType !== 'PDF' && (
                    <button onClick={() => playFrom(seg.startSeconds)}>Play</button>
                  )}
                </article>
              ))}
            </div>
          </section>

          {/* Media preview */}
          <section className="panel media-panel">
            <div className="section-header">
              <div>
                <p className="eyebrow">Media Preview</p>
                <h2>Jump to the right moment</h2>
              </div>
            </div>
            {selectedAsset?.assetType === 'VIDEO' && (
              <video ref={mediaRef} controls src={`${API_BASE}${selectedAsset.mediaUrl}`} className="media-player" />
            )}
            {selectedAsset?.assetType === 'AUDIO' && (
              <audio ref={mediaRef} controls src={`${API_BASE}${selectedAsset.mediaUrl}`} className="media-player" />
            )}
            {selectedAsset?.assetType === 'PDF' && (
              <iframe title="pdf-preview" src={`${API_BASE}${selectedAsset.mediaUrl}`} className="pdf-frame" />
            )}
            {!selectedAsset && <p style={{ color: 'var(--muted)' }}>Select an asset to preview it here.</p>}
          </section>
        </main>
      </div>
    </>
  )
}

function formatTimestamp(value) {
  const totalSeconds = Math.max(0, Math.floor(value || 0))
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

export default App
