import type { Metadata } from "next";
import { Space_Grotesk, IBM_Plex_Mono } from "next/font/google";
import { SiteHeader } from "@/components/site-header";
import "./globals.css";

const heading = Space_Grotesk({
  variable: "--font-heading",
  subsets: ["latin"],
});

const mono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "URR - 우르르",
  description: "URR - 가장 빠른 티켓팅 플랫폼",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className={`${heading.variable} ${mono.variable} bg-slate-50`}>
        <SiteHeader />
        <main className="mx-auto min-h-[calc(100vh-72px)] max-w-7xl px-6 py-8">{children}</main>
      </body>
    </html>
  );
}
