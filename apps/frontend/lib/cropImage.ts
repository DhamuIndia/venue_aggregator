

export async function getCroppedImg(
    imageSrc: string,
    pixelCrop: {
        x: number;
        y: number;
        width: number;
        height: number;
    }
) {
    const image = new Image();

    image.src = imageSrc;

    await new Promise((resolve) => {
        image.onload = resolve;
    });

    const canvas = document.createElement("canvas");

    const ctx = canvas.getContext("2d");

    if (!ctx) {
        throw new Error("Canvas not supported");
    }

    canvas.width = pixelCrop.width;
    canvas.height = pixelCrop.height;

    ctx.drawImage(
        image,
        pixelCrop.x,
        pixelCrop.y,
        pixelCrop.width,
        pixelCrop.height,
        0,
        0,
        pixelCrop.width,
        pixelCrop.height
    );

    return new Promise<Blob>((resolve) => {
        canvas.toBlob(
            (blob) => {
                if (!blob) {
                    throw new Error("Crop failed");
                }

                resolve(blob);
            },
            "image/jpeg",
            0.95
        );
    });
}