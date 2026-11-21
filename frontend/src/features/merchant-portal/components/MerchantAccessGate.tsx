import { useEffect, useState } from 'react';
import { Outlet } from 'react-router';
import { setAccountBlockedHandler, type AccountBlock } from '../../../api/client';
import AccountNotActivePage from '../pages/AccountNotActivePage';

/**
 * Wraps the merchant area. When any request is refused because the merchant isn't active any more
 * (suspended, rejected or not yet approved), the whole area is replaced by one clear page with a
 * sign-out button, instead of each screen showing its own error card.
 */
export default function MerchantAccessGate() {
  const [block, setBlock] = useState<AccountBlock | null>(null);
  // Bumped by "Check again", so every screen in the area mounts and loads afresh.
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setAccountBlockedHandler(setBlock);
    return () => setAccountBlockedHandler(null);
  }, []);

  if (block) {
    return (
      <AccountNotActivePage
        block={block}
        onCheckAgain={() => {
          setBlock(null);
          setAttempt((n) => n + 1);
        }}
      />
    );
  }
  return <Outlet key={attempt} />;
}
