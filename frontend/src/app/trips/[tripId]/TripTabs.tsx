"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";

const tabs = [
  { path: "", label: "개요" },
  { path: "/survey", label: "설문" },
  { path: "/roles", label: "역할" },
  { path: "/stays", label: "숙소" },
];

export default function TripTabs() {
  const { tripId } = useParams<{ tripId: string }>();
  const pathname = usePathname();
  const base = `/trips/${tripId}`;

  return (
    <nav className="flex gap-1 overflow-x-auto border-b border-line">
      {tabs.map((tab) => {
        const href = base + tab.path;
        const active = pathname === href;
        return (
          <Link
            key={tab.label}
            href={href}
            className={`shrink-0 border-b-2 px-4 py-2 text-sm ${
              active ? "border-accent font-semibold text-accent" : "border-transparent text-muted"
            }`}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
