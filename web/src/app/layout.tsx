import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Brasfoot 2026 Cloud — Multiplayer & Salas Online",
  description: "Jogue Brasfoot 2026 (Mod v2026.1 Beta v1) online no navegador com seus amigos em salas compartilhadas.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className="dark">
      <body className="antialiased selection:bg-emerald-500 selection:text-black">
        {children}
      </body>
    </html>
  );
}
