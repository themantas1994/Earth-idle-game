import { useGameStore } from '../../store/useGameStore';
import { MILESTONE_BY_ID } from '../../engine/milestones';
import { formatDuration } from '../../engine/format';

/**
 * The run's world-news feed: every milestone headline this Earth has
 * triggered, newest first. It is the narrative counterpart to the
 * Atmosphere screen's numbers — the same events, told as consequences.
 */
export default function NewsFeed({ limit }: { limit?: number }) {
  const newsFeed = useGameStore((s) => s.state.newsFeed);
  const items = limit ? newsFeed.slice(0, limit) : newsFeed;

  return (
    <div className="card">
      <div className="card__title">📰 World News</div>
      {items.length === 0 && (
        <div className="empty-hint">Nothing newsworthy yet. Keep burning things.</div>
      )}
      {items.map((item) => {
        const def = MILESTONE_BY_ID[item.milestoneId];
        if (!def) return null;
        return (
          <article className="news-item" key={`${item.milestoneId}-${item.at}`}>
            <div className="news-item__kicker">
              {def.source} · {formatDuration(item.runSeconds)} into this Earth
            </div>
            <div className="news-item__headline">{def.icon} {def.headline}</div>
            <div className="news-item__body">{def.body}</div>
          </article>
        );
      })}
      {limit !== undefined && newsFeed.length > limit && (
        <div className="news-item__kicker" style={{ marginTop: 8 }}>
          + {newsFeed.length - limit} earlier {newsFeed.length - limit === 1 ? 'headline' : 'headlines'} this run
        </div>
      )}
    </div>
  );
}
