import * as React from "react";
import { cn } from "@/lib/utils";

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "default" | "secondary" | "success" | "warning" | "destructive" | "outline";
}

export function Badge({ className, variant = "default", ...props }: BadgeProps) {
  return (
    <div
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2",
        {
          "bg-indigo-100 text-indigo-800": variant === "default",
          "bg-slate-100 text-slate-800": variant === "secondary",
          "bg-emerald-100 text-emerald-800": variant === "success",
          "bg-amber-100 text-amber-800": variant === "warning",
          "bg-rose-100 text-rose-800": variant === "destructive",
          "border border-slate-200 text-slate-700": variant === "outline",
        },
        className
      )}
      {...props}
    />
  );
}
