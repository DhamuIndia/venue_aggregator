import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Venue Aggregator",
  description: "Find halls and event-service vendors for your next event."
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
