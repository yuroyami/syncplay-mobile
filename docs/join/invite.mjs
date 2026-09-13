export function parseInvite(fragment) {
  if (!fragment || fragment.length > 16384 || !/^[A-Za-z0-9_=-]+$/.test(fragment)) return null;
  try {
    const binary = atob(fragment.replaceAll('-', '+').replaceAll('_', '/'));
    const text = new TextDecoder('utf-8', {fatal: true}).decode(Uint8Array.from(binary, c => c.charCodeAt(0)));
    const url = new URL(text);
    if (url.protocol !== 'synkplay:' || url.hostname !== 'join' || url.pathname || url.username || url.password) return null;
    const room = url.searchParams.get('room')?.trim();
    if (!room) return null;
    return {url: url.href, room: room.slice(0, 35)};
  } catch { return null; }
}

if (typeof document !== 'undefined') {
  const title = document.getElementById('title');
  const description = document.getElementById('description');
  const open = document.getElementById('open');
  const copy = document.getElementById('copy');
  const status = document.getElementById('status');
  function renderInvite() {
    const invite = parseInvite(location.hash.slice(1));
    title.textContent = invite ? `Join ${invite.room}` : 'Watch together.';
    description.textContent = invite
      ? 'Your room details are ready. Open Synkplay to join.'
      : location.hash
        ? 'This invitation is incomplete. Ask your friend to share it again.'
        : 'Open this page from a room invitation to join your friends.';
    open.hidden = copy.hidden = !invite;
    if (invite) open.href = invite.url;
    else open.removeAttribute('href');
    status.textContent = '';
  }
  copy.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(location.href);
      status.textContent = 'Copied. You can also paste it into Synkplay’s room field.';
    } catch {
      status.textContent = 'Copy this page’s address, then paste it into Synkplay’s room field.';
    }
  });
  window.addEventListener('hashchange', renderInvite);
  renderInvite();
}
