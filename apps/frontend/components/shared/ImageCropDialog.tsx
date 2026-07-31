"use client";

import Cropper from "react-easy-crop";
import { useState } from "react";
import type { Area } from "react-easy-crop";
import { getCroppedImg } from "@/lib/cropImage";

type ImageCropDialogProps = {
    open: boolean;
    image: string;
    onCancel: () => void;
    onSave: (blob: Blob) => void;
};

export default function ImageCropDialog({
    open,
    image,
    onCancel,
    onSave,
}: ImageCropDialogProps) {

    const [crop, setCrop] = useState({ x: 0, y: 0 });

    const [zoom, setZoom] = useState(1);

    const [croppedAreaPixels, setCroppedAreaPixels] =
        useState<Area | null>(null);

    const onCropComplete = (
        croppedArea: Area,
        croppedAreaPixels: Area
    ) => {
        setCroppedAreaPixels(croppedAreaPixels);
    };

    const handleSave = async () => {

        if (!croppedAreaPixels) return;

        const blob = await getCroppedImg(
            image,
            croppedAreaPixels
        );

        onSave(blob);
    };

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70">
            <div className="w-full max-w-3xl rounded-xl bg-white p-6 shadow-xl">

                <h2 className="text-xl font-semibold">
                    Adjust Cover Image
                </h2>

                <div className="relative mt-5 h-[450px] overflow-hidden rounded-lg bg-gray-900">

                    <Cropper
                        image={image}
                        crop={crop}
                        zoom={zoom}
                        aspect={16 / 9}
                        onCropChange={setCrop}
                        onZoomChange={setZoom}
                        onCropComplete={onCropComplete}
                    />

                    <div className="mt-4">
                        <input
                            type="range"
                            min={1}
                            max={3}
                            step={0.1}
                            value={zoom}
                            onChange={(e) => setZoom(Number(e.target.value))}
                            className="w-full"
                        />
                    </div>

                </div>

                <div className="mt-5 flex justify-end gap-3">

                    <button
                        onClick={onCancel}
                        className="rounded-md border px-5 py-2"
                    >
                        Cancel
                    </button>

                    <button
                        onClick={handleSave}
                        className="rounded-md bg-primary px-5 py-2 text-white"
                    >
                        Save
                    </button>

                </div>

            </div>
        </div>
    );
}