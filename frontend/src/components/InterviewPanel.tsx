import { useCallback, useEffect, useState } from 'react';
import { api, formatDuration, INTERVIEW_OUTCOMES, saveBlob } from '../api/client';
import type { Interview, InterviewOutcome, InterviewPayload } from '../api/client';
import Markdown from './Markdown';

// Interview rounds for one application, with per-round prep notes.
// Prep notes are edited as Markdown source and rendered through the
// Markdown component; the API stores and returns source, never HTML.

interface Props {
  applicationId: number;
}

function toPayload(interview: Interview, changes: Partial<InterviewPayload>): InterviewPayload {
  return {
    round: interview.round,
    scheduledAt: interview.scheduledAt,
    durationMinutes: interview.durationMinutes,
    interviewer: interview.interviewer ?? undefined,
    outcome: interview.outcome,
    notes: interview.notes ?? undefined,
    prepNotes: interview.prepNotes ?? undefined,
    ...changes,
  };
}

export default function InterviewPanel({ applicationId }: Props) {
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [round, setRound] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [interviewer, setInterviewer] = useState('');
  const [editingPrepId, setEditingPrepId] = useState<number | null>(null);
  const [prepDraft, setPrepDraft] = useState('');

  const reload = useCallback(async () => {
    try {
      setInterviews(await api.listInterviews(applicationId));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load interviews');
    }
  }, [applicationId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api.createInterview(applicationId, {
        round: round.trim(),
        // datetime-local has no zone; treat the entered time as local and
        // send the equivalent instant, which is what the API stores.
        scheduledAt: new Date(scheduledAt).toISOString(),
        durationMinutes,
        interviewer: interviewer.trim() || undefined,
      });
      setRound('');
      setScheduledAt('');
      setDurationMinutes(60);
      setInterviewer('');
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add interview');
    }
  }

  async function handleOutcome(interview: Interview, outcome: InterviewOutcome) {
    try {
      await api.updateInterview(applicationId, interview.id, toPayload(interview, { outcome }));
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update interview');
    }
  }

  async function handleCalendar(interview: Interview) {
    try {
      saveBlob(
        await api.downloadInterviewCalendar(applicationId, interview.id),
        `interview-${interview.id}.ics`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to download calendar file');
    }
  }

  async function handleSavePrep(interview: Interview) {
    try {
      await api.updateInterview(
        applicationId,
        interview.id,
        toPayload(interview, { prepNotes: prepDraft }),
      );
      setEditingPrepId(null);
      await reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save prep notes');
    }
  }

  return (
    <div className="interview-panel">
      {error && <p className="error">{error}</p>}

      {interviews.length === 0 && <p className="muted">No interview rounds scheduled yet.</p>}

      {interviews.map((interview) => (
        <div key={interview.id} className="interview-card">
          <div className="row space-between">
            <strong>{interview.round}</strong>
            <span className="row">
              <span className="muted small">
                {new Date(interview.scheduledAt).toLocaleString()}
                {` · ${formatDuration(interview.durationMinutes)}`}
                {interview.interviewer ? ` — ${interview.interviewer}` : ''}
              </span>
              <select
                className="status"
                aria-label={`Outcome for ${interview.round}`}
                value={interview.outcome}
                onChange={(e) =>
                  void handleOutcome(interview, e.target.value as InterviewOutcome)
                }
              >
                {INTERVIEW_OUTCOMES.map((outcome) => (
                  <option key={outcome} value={outcome}>
                    {outcome}
                  </option>
                ))}
              </select>
              {interview.outcome === 'SCHEDULED' && (
                <button
                  className="secondary"
                  onClick={() => void handleCalendar(interview)}
                >
                  Calendar (.ics)
                </button>
              )}
            </span>
          </div>

          {interview.notes && <p className="small">{interview.notes}</p>}

          <div className="row space-between">
            <span className="small muted">Prep notes</span>
            {editingPrepId !== interview.id && (
              <button
                className="link-button"
                onClick={() => {
                  setEditingPrepId(interview.id);
                  setPrepDraft(interview.prepNotes ?? '');
                }}
              >
                {interview.prepNotes ? 'Edit' : 'Add prep notes'}
              </button>
            )}
          </div>

          {editingPrepId === interview.id ? (
            <div>
              <textarea
                rows={8}
                value={prepDraft}
                onChange={(e) => setPrepDraft(e.target.value)}
                placeholder={
                  '## Focus areas\n- Review **caching** tradeoffs\n- Questions to ask the panel'
                }
                aria-label={`Prep notes for ${interview.round}`}
              />
              <div className="row">
                <button onClick={() => void handleSavePrep(interview)}>Save prep notes</button>
                <button className="secondary" onClick={() => setEditingPrepId(null)}>
                  Cancel
                </button>
              </div>
              {prepDraft.trim() && (
                <div className="prep-preview">
                  <span className="small muted">Preview</span>
                  <Markdown source={prepDraft} />
                </div>
              )}
            </div>
          ) : interview.prepNotes ? (
            <Markdown source={interview.prepNotes} />
          ) : (
            <p className="muted small">Nothing written yet.</p>
          )}
        </div>
      ))}

      <form onSubmit={(e) => void handleAdd(e)} className="interview-add">
        <label>
          Round
          <input
            value={round}
            onChange={(e) => setRound(e.target.value)}
            placeholder="Technical deep dive"
            required
          />
        </label>
        <label>
          When
          <input
            type="datetime-local"
            value={scheduledAt}
            onChange={(e) => setScheduledAt(e.target.value)}
            required
          />
        </label>
        <label>
          Duration (minutes)
          <input
            type="number"
            min={15}
            max={480}
            step={15}
            value={durationMinutes}
            onChange={(e) => setDurationMinutes(Number(e.target.value))}
            required
          />
        </label>
        <label>
          Interviewer
          <input
            value={interviewer}
            onChange={(e) => setInterviewer(e.target.value)}
            placeholder="Optional"
          />
        </label>
        <button type="submit">Add round</button>
      </form>
    </div>
  );
}
