"use client";

import { useRouter } from "next/navigation";
import Avatar from "@/app/Avatar";

// POST: /api/logout  -> 204
export default function UserMenu({ name, imageUrl }: { name: string; imageUrl: string | null }) {
  const router = useRouter();

  async function logout() {
    await fetch("/api/logout", { method: "POST" });
    router.replace("/");
    router.refresh();
  }

  return (
    <div className="flex items-center gap-2 text-sm text-muted">
      <Avatar name={name} imageUrl={imageUrl} size={24} />
      <span>{name}</span>
      <button onClick={logout} className="underline">로그아웃</button>
    </div>
  );
}
