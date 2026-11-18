"use client";

import { useState } from "react";

export default function InviteButton({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);
  const link = typeof window === "undefined" ? `/join/${code}` : `${window.location.origin}/join/${code}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // 클립보드 권한이 없으면 링크를 직접 보여 줘서 손으로 복사하게 한다
      window.prompt("이 링크를 복사해 친구에게 보내 주세요", link);
    }
  }

  return (
    <button onClick={copy} className="text-sm text-accent" aria-live="polite">
      {copied ? "복사했어요" : "초대 링크 복사"}
    </button>
  );
}
