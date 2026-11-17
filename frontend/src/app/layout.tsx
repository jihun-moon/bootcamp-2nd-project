import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TripMate",
  description: "다인원 여행 역할 배정과 숙소 투표",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
