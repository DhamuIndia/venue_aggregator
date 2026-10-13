import type { PublicPhotoCredit as Credit } from "@/features/halls/types";

// Public credit data is a publication snapshot. Never render private source
// references, permission evidence, review reasons or admin identities here.
export function PublicPhotoCredit({ credit, className = "" }: { credit: Credit | null | undefined; className?: string }) {
  if (!credit) return null;
  const link = "font-medium underline underline-offset-2";
  return <div aria-label={`Photo credit: ${credit.title}`} className={`break-words text-xs leading-5 ${className}`}>
    <p><strong>{credit.title}</strong> · {credit.creatorUrl ? <a className={link} href={credit.creatorUrl} rel="noopener noreferrer" target="_blank">{credit.creator}</a> : credit.creator} · <a className={link} href={credit.sourceUrl} rel="noopener noreferrer" target="_blank">Photo source</a> · <a className={link} href={credit.licenseUrl} rel="noopener noreferrer" target="_blank">{credit.licenseLabel}</a></p>
    <p className="mt-1 whitespace-pre-wrap">{credit.changesNotice}</p>
    <p className="whitespace-pre-wrap">{credit.processingNotice}</p>
    {credit.requiredNotices && <p className="mt-1 whitespace-pre-wrap">{credit.requiredNotices}</p>}
  </div>;
}
