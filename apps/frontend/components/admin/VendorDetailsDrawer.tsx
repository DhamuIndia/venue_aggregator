"use client";

import Image from "next/image";
import { Check, X, XCircle } from "lucide-react";
import type { VendorApplication } from "@/features/admin/mock-data";

type Props = {
    vendor: VendorApplication;
    onClose: () => void;
    onApprove: () => void;
    onReject: () => void;
};

export function VendorDetailsDrawer({
    vendor,
    onClose,
    onApprove,
    onReject,
}: Props) {
    return (
        <>
            {/* Background */}
            <div
                className="fixed inset-0 z-40 bg-black/40"
                onClick={onClose}
            />

            {/* Drawer */}
            <div className="fixed right-0 top-0 z-50 h-screen w-full max-w-xl overflow-y-auto bg-white shadow-2xl">

                {/* Header */}
                <div className="sticky top-0 flex items-center justify-between border-b bg-white px-6 py-4">
                    <h2 className="text-xl font-semibold">
                        Vendor Details
                    </h2>

                    <button
                        onClick={onClose}
                        className="rounded-md p-2 hover:bg-gray-100"
                    >
                        <X size={20} />
                    </button>
                </div>

                <div className="space-y-6 p-6">

                    {/* Cover Image */}
                    {vendor.coverImageUrl && (
                        <div className="relative h-52 overflow-hidden rounded-lg border">
                            <Image
                                src={vendor.coverImageUrl}
                                alt={vendor.businessName}
                                fill
                                className="object-cover"
                                unoptimized
                            />
                        </div>
                    )}

                    {/* Business Details */}

                    <Section title="Business Details">

                        <Field
                            label="Business Name"
                            value={vendor.businessName}
                        />

                        <Field
                            label="Contact Person"
                            value={vendor.contactName}
                        />

                        <Field
                            label="Category"
                            value={vendor.category}
                        />

                        <Field
                            label="City"
                            value={vendor.city}
                        />

                        <Field
                            label="Status"
                            value={vendor.status}
                        />

                    </Section>

                    {/* Contact */}

                    <Section title="Contact">

                        <Field
                            label="Phone"
                            value={vendor.phone}
                        />

                        <Field
                            label="Email"
                            value={vendor.email}
                        />

                    </Section>

                    {/* Description */}

                    <Section title="Description">

                        <p className="text-sm text-gray-700">
                            {vendor.description}
                        </p>

                    </Section>

                    {/* Services */}

                    <Section title="Services">

                        <div className="flex flex-wrap gap-2">

                            {vendor.services?.map((service) => (
                                <span
                                    key={service}
                                    className="rounded-full bg-blue-50 px-3 py-1 text-sm"
                                >
                                    {service}
                                </span>
                            ))}

                        </div>

                    </Section>

                    {/* Package */}

                    <Section title="Package">

                        <Field
                            label="Package Name"
                            value={vendor.packageName}
                        />

                        <Field
                            label="Starting Price"
                            value={`₹${vendor.startingPrice}`}
                        />

                        <Field
                            label="Package Description"
                            value={vendor.packageDescription}
                        />

                    </Section>

                    {/* Action Buttons */}

                    {vendor.status === "PENDING_APPROVAL" && (

                        <div className="flex gap-3 pt-4">

                            <button
                                onClick={onReject}
                                className="flex-1 rounded-md border border-red-200 py-3 font-semibold text-red-700 hover:bg-red-50"
                            >
                                <span className="flex items-center justify-center gap-2">
                                    <XCircle size={18} />
                                    Reject
                                </span>
                            </button>

                            <button
                                onClick={onApprove}
                                className="flex-1 rounded-md bg-primary py-3 font-semibold text-white"
                            >
                                <span className="flex items-center justify-center gap-2">
                                    <Check size={18} />
                                    Approve
                                </span>
                            </button>

                        </div>

                    )}

                </div>

            </div>
        </>
    );
}

function Section({
    title,
    children,
}: {
    title: string;
    children: React.ReactNode;
}) {
    return (
        <div>

            <h3 className="mb-3 text-lg font-semibold">
                {title}
            </h3>

            <div className="space-y-3">
                {children}
            </div>

        </div>
    );
}

function Field({
    label,
    value,
}: {
    label: string;
    value?: string | number | null;
}) {
    return (
        <div className="rounded-md border p-3">

            <div className="text-xs text-gray-500">
                {label}
            </div>

            <div className="mt-1 font-medium">
                {value || "-"}
            </div>

        </div>
    );
}