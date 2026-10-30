import type { UploadDraft } from "../types/imaging";
export async function readDicomFiles(
  files: File[],
  batch: number,
): Promise<UploadDraft["files"]> {
  return Promise.all(
    files.map(async (f) => {
      const bytes = new Uint8Array(await f.slice(0, 132).arrayBuffer());
      const valid =
        f.name.toLowerCase().endsWith(".dcm") &&
        bytes.length >= 132 &&
        String.fromCharCode(...bytes.slice(128, 132)) === "DICM";
      return { name: `Đợt ${batch} · ${f.name}`, size: f.size, valid };
    }),
  );
}
