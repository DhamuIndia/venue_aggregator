export const MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB

export const ALLOWED_IMAGE_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp",
];

export const MIN_IMAGE_WIDTH = 1280;
export const MIN_IMAGE_HEIGHT = 720;

export async function validateImage(file: File): Promise<string | null> {

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        return "Only JPG, PNG and WebP images are allowed.";
    }

    if (file.size > MAX_IMAGE_SIZE) {
        return "Image size must not exceed 5 MB.";
    }

    try {

        const { width, height } = await getImageDimensions(file);

        if (
            width < MIN_IMAGE_WIDTH ||
            height < MIN_IMAGE_HEIGHT
        ) {
            return `Image resolution is too low.

Current: ${width} × ${height}
Required: at least ${MIN_IMAGE_WIDTH} × ${MIN_IMAGE_HEIGHT}`;
        }

    } catch {

        return "The selected image is invalid or corrupted.";

    }

    return null;
}

async function getImageDimensions(
    file: File
): Promise<{ width: number; height: number }> {

    return new Promise((resolve, reject) => {

        const image = new Image();

        const objectUrl = URL.createObjectURL(file);

        image.onload = () => {

            resolve({
                width: image.width,
                height: image.height,
            });

            URL.revokeObjectURL(objectUrl);
        };

        image.onerror = () => {

            URL.revokeObjectURL(objectUrl);

            reject(new Error("Invalid image."));
        };

        image.src = objectUrl;
    });
}