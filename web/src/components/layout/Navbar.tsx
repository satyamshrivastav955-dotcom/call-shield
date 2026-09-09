"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { siteContent } from "@/content/content";
import { fetchModelDebug } from "@/lib/api";

export function Navbar() {
  const pathname = usePathname();
  const [isLive, setIsLive] = useState<boolean | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    let mounted = true;
    fetchModelDebug().then((res) => {
      if (mounted) setIsLive(res.isLive);
    });
    const interval = setInterval(() => {
      fetchModelDebug().then((res) => {
        if (mounted) setIsLive(res.isLive);
      });
    }, 8000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-cyber-border bg-cyber-chassis/95 backdrop-blur-md">
      {/* Topmost Tactical Security Classification Banner */}
      <div className="border-b border-cyber-border/70 bg-black/60 px-4 py-1 text-[10px] font-mono text-slate-400">
        <div className="mx-auto flex max-w-7xl items-center justify-between">
          <div className="flex items-center gap-2 tracking-wider">
            <span className="text-cyber-blue-light font-bold">CLASSIFICATION:</span>
            <span className="text-slate-300">OPERATIONAL // SIH-PS26104</span>
            <span className="hidden md:inline text-slate-600">|</span>
            <span className="hidden md:inline text-slate-400">TAP: WEBRTC_V1_TAP</span>
            <span className="hidden lg:inline text-slate-600">|</span>
            <span className="hidden lg:inline text-slate-400">POLICY: ZERO-TRUST DETERMINISTIC</span>
          </div>
          <div className="flex items-center gap-3 font-mono">
            <span className="text-emerald-400 font-semibold flex items-center gap-1">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
              SHIELD ACTIVE
            </span>
            <span className="text-slate-500 hidden sm:inline">{siteContent.meta.nodeId}</span>
          </div>
        </div>
      </div>

      {/* Main Tactical Navbar */}
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-2.5 sm:px-6 lg:px-8">
        {/* Brand */}
        <Link
          href="/"
          className="flex items-center gap-2.5 focus:outline-none focus:ring-1 focus:ring-cyber-blue"
        >
          <div className="flex h-8 w-8 items-center justify-center rounded border border-cyber-blue/60 bg-cyber-surface text-cyber-blue-light font-black text-sm shadow-sm relative">
            <span className="absolute -top-1 -left-1 text-[8px] text-cyber-blue leading-none">+</span>
            <span className="absolute -bottom-1 -right-1 text-[8px] text-cyber-blue leading-none">+</span>
            <svg
              className="h-4 w-4 text-cyber-blue-light"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              <circle cx="12" cy="11" r="3" />
            </svg>
          </div>
          <div>
            <div className="flex items-center gap-1.5 leading-none">
              <span className="font-mono font-black text-white text-base tracking-tight">antAI</span>
              <span className="text-[11px] font-mono font-bold text-cyber-blue-light tracking-wider uppercase">
                GUARDIAN
              </span>
            </div>
            <p className="text-[9px] text-slate-500 font-mono tracking-wider uppercase mt-0.5">
              Telephony Threat Intercept
            </p>
          </div>
        </Link>

        {/* Desktop Nav Links */}
        <nav className="hidden md:flex items-center gap-1 font-mono text-xs" aria-label="Main Navigation">
          {siteContent.navigation.links.map((link) => {
            const active = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`rounded px-3 py-1.5 transition-all text-[11px] uppercase tracking-wider ${
                  active
                    ? "bg-cyber-surface-elevated text-cyber-blue-light font-bold border border-cyber-border-highlight shadow-sm"
                    : "text-slate-400 hover:bg-cyber-surface hover:text-slate-200"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        {/* Tactical Status Pill & Access Button */}
        <div className="flex items-center gap-3">
          <div
            className="flex items-center gap-2 rounded border border-cyber-border bg-cyber-surface px-2.5 py-1 text-[10px] font-mono text-slate-300"
            title={isLive ? "Connected to live FastAPI backend at localhost:8765" : "Backend offline — operating in Graceful Demo Mode"}
          >
            <span
              className={`h-2 w-2 rounded-full ${
                isLive === null
                  ? "bg-slate-500 animate-pulse"
                  : isLive
                  ? "bg-emerald-400 shadow-[0_0_8px_#10b981]"
                  : "bg-amber-400 shadow-[0_0_8px_#f59e0b]"
              }`}
            />
            <span className="hidden sm:inline uppercase">
              {isLive === null ? "PROBING..." : isLive ? "API ONLINE" : "DEMO CLUSTER"}
            </span>
          </div>

          <Link
            href="/dashboard-live"
            className="hidden sm:inline-flex items-center gap-2 rounded border border-cyber-blue bg-cyber-blue/15 hover:bg-cyber-blue hover:text-white px-3.5 py-1.5 text-xs font-mono font-bold text-cyber-blue-light transition-all shadow-sm"
          >
            <span className="h-1.5 w-1.5 rounded-full bg-cyber-blue-light animate-ping" />
            [ SOC CONSOLE ]
          </Link>

          {/* Mobile menu toggle */}
          <button
            type="button"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="md:hidden rounded border border-cyber-border p-1.5 text-slate-400 hover:bg-cyber-surface"
            aria-label="Toggle Navigation"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              {mobileMenuOpen ? (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              ) : (
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              )}
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile Menu Dropdown */}
      {mobileMenuOpen && (
        <div className="md:hidden border-b border-cyber-border bg-cyber-chassis px-4 py-3 space-y-1 font-mono text-xs">
          {siteContent.navigation.links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              onClick={() => setMobileMenuOpen(false)}
              className={`block rounded px-3 py-2 uppercase tracking-wider ${
                pathname === link.href
                  ? "bg-cyber-surface-elevated text-cyber-blue-light font-bold"
                  : "text-slate-400 hover:bg-cyber-surface hover:text-white"
              }`}
            >
              {link.label}
            </Link>
          ))}
          <div className="pt-2">
            <Link
              href="/dashboard-live"
              onClick={() => setMobileMenuOpen(false)}
              className="block w-full text-center rounded border border-cyber-blue bg-cyber-blue/20 py-2 text-xs font-bold text-cyber-blue-light"
            >
              [ LAUNCH SOC CONSOLE ]
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
