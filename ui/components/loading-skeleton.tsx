export function LoadingSkeleton() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {[0, 1, 2].map(i => (
        <div
          key={i}
          style={{
            borderRadius: 10,
            border: '1px solid var(--card-border)',
            background: 'var(--card-bg)',
            padding: '16px 20px 14px 22px',
            overflow: 'hidden',
            opacity: 1 - i * 0.2,
            position: 'relative',
          }}
        >
          {/* left bar placeholder */}
          <div style={{
            position: 'absolute', left: 0, top: 0, bottom: 0, width: 3,
            background: 'var(--card-border)',
          }} />
          <Shimmer width="38%" height={14} mb={16} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 8 }}>
            <div><Shimmer width="60%" height={10} mb={6} /><Shimmer width="40%" height={22} mb={0} /></div>
            <div><Shimmer width="60%" height={10} mb={6} /><Shimmer width="30%" height={22} mb={0} /></div>
            <div><Shimmer width="60%" height={10} mb={6} /><Shimmer width="35%" height={22} mb={0} /></div>
          </div>
        </div>
      ))}
    </div>
  );
}

function Shimmer({ width, height, mb }: { width: string; height: number; mb: number }) {
  return (
    <div style={{
      width,
      height,
      marginBottom: mb,
      borderRadius: 4,
      background: 'linear-gradient(90deg, var(--card-border) 25%, var(--card-hover, var(--card-bg)) 50%, var(--card-border) 75%)',
      backgroundSize: '200% 100%',
      animation: 'shimmer 1.6s ease-in-out infinite',
    }} />
  );
}

// Inject shimmer keyframe once
if (typeof document !== 'undefined') {
  const id = '__shimmer_kf__';
  if (!document.getElementById(id)) {
    const s = document.createElement('style');
    s.id = id;
    s.textContent = `@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`;
    document.head.appendChild(s);
  }
}
