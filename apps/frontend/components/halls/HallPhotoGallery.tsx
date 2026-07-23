"use client";

import { ChevronLeft, ChevronRight, Expand, X } from "lucide-react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";

type HallPhotoGalleryProps = {
  coverImage: string;
  galleryImages: string[];
  hallName: string;
};

export function HallPhotoGallery({ coverImage, galleryImages, hallName }: HallPhotoGalleryProps) {
  const images = [coverImage, ...galleryImages]
    .filter((image, index, urls) => Boolean(image) && urls.indexOf(image) === index);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isViewerOpen, setIsViewerOpen] = useState(false);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const selectedImage = images[selectedIndex];

  useEffect(() => {
    if (!isViewerOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeButtonRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setIsViewerOpen(false);
      if (event.key === "ArrowLeft") {
        setSelectedIndex((current) => (current - 1 + images.length) % images.length);
      }
      if (event.key === "ArrowRight") {
        setSelectedIndex((current) => (current + 1) % images.length);
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [images.length, isViewerOpen]);

  function openPhoto(index: number) {
    setSelectedIndex(index);
    setIsViewerOpen(true);
  }

  function showPrevious() {
    setSelectedIndex((current) => (current - 1 + images.length) % images.length);
  }

  function showNext() {
    setSelectedIndex((current) => (current + 1) % images.length);
  }

  return (
    <>
      <section aria-label={`${hallName} photo gallery`} className="mt-5 overflow-hidden rounded-lg">
        <button
          aria-label={`Open ${hallName} cover photo full screen`}
          className="group relative block aspect-[16/9] w-full cursor-zoom-in overflow-hidden bg-muted sm:aspect-[2/1]"
          onClick={() => openPhoto(0)}
          type="button"
        >
          <Image alt={`${hallName} main hall`} className="object-cover" fill priority sizes="(max-width: 1280px) 100vw, 1280px" src={coverImage} />
          <span className="absolute bottom-3 right-3 inline-flex items-center gap-2 rounded-md bg-black/70 px-3 py-2 text-xs font-semibold text-white transition group-hover:bg-black/85">
            <Expand aria-hidden="true" size={15} /> View full size
          </span>
        </button>

        {images.length > 1 && (
          <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {images.slice(1).map((image, index) => (
              <button
                aria-label={`Open ${hallName} photo ${index + 2} full screen`}
                className="group relative aspect-[4/3] cursor-zoom-in overflow-hidden rounded-md bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
                key={image}
                onClick={() => openPhoto(index + 1)}
                type="button"
              >
                <Image alt={`${hallName} gallery view ${index + 1}`} className="object-cover transition duration-200 group-hover:scale-[1.02]" fill sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw" src={image} />
                <span className="absolute inset-0 grid place-items-center bg-black/0 text-white opacity-0 transition group-hover:bg-black/25 group-hover:opacity-100">
                  <Expand aria-hidden="true" size={24} />
                </span>
              </button>
            ))}
          </div>
        )}
      </section>

      {isViewerOpen && selectedImage && (
        <div
          aria-label={`${hallName} full-screen photo viewer`}
          aria-modal="true"
          className="fixed inset-0 z-[80] grid place-items-center bg-black/95 p-3 sm:p-6"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) setIsViewerOpen(false);
          }}
          role="dialog"
        >
          <div className="relative h-[calc(100vh-6rem)] w-full max-w-7xl">
            <Image alt={`${hallName} photo ${selectedIndex + 1} full size`} className="object-contain" fill priority sizes="100vw" src={selectedImage} />
          </div>

          <div className="absolute left-3 top-3 rounded-md bg-black/60 px-3 py-2 text-sm font-medium text-white sm:left-6 sm:top-6">
            {selectedIndex + 1} of {images.length}
          </div>
          <button
            aria-label="Close full-screen photo"
            className="absolute right-3 top-3 grid size-11 place-items-center rounded-full bg-black/60 text-white hover:bg-black/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white sm:right-6 sm:top-6"
            onClick={() => setIsViewerOpen(false)}
            ref={closeButtonRef}
            type="button"
          >
            <X aria-hidden="true" size={24} />
          </button>

          {images.length > 1 && (
            <>
              <button
                aria-label="Previous photo"
                className="absolute left-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-black/60 text-white hover:bg-black/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white sm:left-6"
                onClick={showPrevious}
                type="button"
              >
                <ChevronLeft aria-hidden="true" size={26} />
              </button>
              <button
                aria-label="Next photo"
                className="absolute right-3 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-black/60 text-white hover:bg-black/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white sm:right-6"
                onClick={showNext}
                type="button"
              >
                <ChevronRight aria-hidden="true" size={26} />
              </button>
            </>
          )}
        </div>
      )}
    </>
  );
}
