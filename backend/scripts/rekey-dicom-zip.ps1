param(
    [Parameter(Mandatory = $true)]
    [string] $InputZip,

    [Parameter(Mandatory = $true)]
    [string] $OutputZip
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

public static class DicomUidPatcher
{
    public static int Find(byte[] data, int group, int element)
    {
        byte[] tag = {
            (byte)(group & 0xff), (byte)((group >> 8) & 0xff),
            (byte)(element & 0xff), (byte)((element >> 8) & 0xff)
        };
        for (int i = 0; i <= data.Length - 8; i++)
        {
            if (data[i] == tag[0] && data[i + 1] == tag[1] &&
                data[i + 2] == tag[2] && data[i + 3] == tag[3] &&
                data[i + 4] == (byte)'U' && data[i + 5] == (byte)'I')
                return i;
        }
        throw new InvalidDataException("DICOM UID tag (" + group.ToString("X4") + "," + element.ToString("X4") + ") was not found.");
    }

    public static string Read(byte[] data, int group, int element)
    {
        int offset = Find(data, group, element);
        int length = data[offset + 6] | (data[offset + 7] << 8);
        if (offset + 8 + length > data.Length)
            throw new InvalidDataException("DICOM UID value exceeds the file length.");
        return Encoding.ASCII.GetString(data, offset + 8, length).Trim('\0', ' ');
    }

    public static void Replace(byte[] data, int group, int element, string uid)
    {
        int offset = Find(data, group, element);
        int length = data[offset + 6] | (data[offset + 7] << 8);
        if (uid.Length > length)
            throw new InvalidDataException("Replacement UID is longer than the existing value for (" + group.ToString("X4") + "," + element.ToString("X4") + ").");
        byte[] value = Encoding.ASCII.GetBytes(uid.PadRight(length, '\0'));
        Buffer.BlockCopy(value, 0, data, offset + 8, length);
    }

    public static string NewUid(string root, int totalLength)
    {
        int digits = totalLength - root.Length;
        if (digits < 2)
            throw new InvalidDataException("Existing UID is too short to create a replacement.");
        var suffix = new StringBuilder(digits);
        using (RandomNumberGenerator rng = RandomNumberGenerator.Create())
        {
            byte[] random = new byte[digits];
            rng.GetBytes(random);
            suffix.Append((char)('1' + (random[0] % 9)));
            for (int i = 1; i < digits; i++)
                suffix.Append((char)('0' + (random[i] % 10)));
        }
        return root + suffix;
    }
}
'@

$inputPath = (Resolve-Path -LiteralPath $InputZip).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputZip)
if ([string]::Equals($inputPath, $outputPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'InputZip and OutputZip must be different files; the source archive is preserved.'
}
if (Test-Path -LiteralPath $outputPath) {
    throw "Output already exists: $outputPath"
}

$source = [System.IO.Compression.ZipFile]::OpenRead($inputPath)
$destination = $null
try {
    $dicomEntries = @($source.Entries | Where-Object { $_.FullName -match '\.dcm$' })
    if ($dicomEntries.Count -eq 0) {
        throw 'The ZIP does not contain any .dcm files.'
    }

    $firstStream = $dicomEntries[0].Open()
    try {
        $firstBuffer = [System.IO.MemoryStream]::new()
        $firstStream.CopyTo($firstBuffer)
        $firstBytes = $firstBuffer.ToArray()
        $firstBuffer.Dispose()
    } finally {
        $firstStream.Dispose()
    }

    $oldStudyUid = [DicomUidPatcher]::Read($firstBytes, 0x0020, 0x000D)
    $oldSeriesUid = [DicomUidPatcher]::Read($firstBytes, 0x0020, 0x000E)
    $root = $oldStudyUid.Substring(0, $oldStudyUid.LastIndexOf('.') + 1)
    $studyUid = [DicomUidPatcher]::NewUid($root, $oldStudyUid.Length)
    $seriesUidMap = @{}
    foreach ($dicomEntry in $dicomEntries) {
        $scanStream = $dicomEntry.Open()
        try {
            $scanBuffer = [System.IO.MemoryStream]::new()
            $scanStream.CopyTo($scanBuffer)
            $scanBytes = $scanBuffer.ToArray()
            $scanBuffer.Dispose()
        } finally {
            $scanStream.Dispose()
        }
        $scanStudyUid = [DicomUidPatcher]::Read($scanBytes, 0x0020, 0x000D)
        if ($scanStudyUid -ne $oldStudyUid) {
            throw "Archive contains more than one study; stopped at $($dicomEntry.FullName)."
        }
        $scanSeriesUid = [DicomUidPatcher]::Read($scanBytes, 0x0020, 0x000E)
        if (-not $seriesUidMap.ContainsKey($scanSeriesUid)) {
            $seriesUidMap[$scanSeriesUid] = [DicomUidPatcher]::NewUid($root, $scanSeriesUid.Length)
        }
    }
    $usedSopUids = [System.Collections.Generic.HashSet[string]]::new()

    $outputDirectory = [System.IO.Path]::GetDirectoryName($outputPath)
    [System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
    $fileStream = [System.IO.File]::Open($outputPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    $destination = [System.IO.Compression.ZipArchive]::new($fileStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)

    foreach ($entry in $source.Entries) {
        $newEntry = $destination.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
        if ($entry.FullName.EndsWith('/')) { continue }
        $newEntry.LastWriteTime = $entry.LastWriteTime

        $inStream = $entry.Open()
        try {
            $buffer = [System.IO.MemoryStream]::new()
            $inStream.CopyTo($buffer)
            $bytes = $buffer.ToArray()
            $buffer.Dispose()
        } finally {
            $inStream.Dispose()
        }

        if ($entry.FullName -match '\.dcm$') {
            $thisStudyUid = [DicomUidPatcher]::Read($bytes, 0x0020, 0x000D)
            $thisSeriesUid = [DicomUidPatcher]::Read($bytes, 0x0020, 0x000E)
            $sopUid = [DicomUidPatcher]::Read($bytes, 0x0008, 0x0018)
            $mediaSopUid = [DicomUidPatcher]::Read($bytes, 0x0002, 0x0003)
            if ($thisStudyUid -ne $oldStudyUid -or -not $seriesUidMap.ContainsKey($thisSeriesUid)) {
                throw "Unexpected study or series UID; stopped at $($entry.FullName)."
            }
            if ($sopUid -ne $mediaSopUid) {
                throw "SOP Instance UID differs from file meta information in $($entry.FullName)."
            }

            do { $newSopUid = [DicomUidPatcher]::NewUid($root, $sopUid.Length) }
            while (-not $usedSopUids.Add($newSopUid))

            [DicomUidPatcher]::Replace($bytes, 0x0020, 0x000D, $studyUid)
            [DicomUidPatcher]::Replace($bytes, 0x0020, 0x000E, $seriesUidMap[$thisSeriesUid])
            [DicomUidPatcher]::Replace($bytes, 0x0008, 0x0018, $newSopUid)
            [DicomUidPatcher]::Replace($bytes, 0x0002, 0x0003, $newSopUid)
        }

        $outStream = $newEntry.Open()
        try { $outStream.Write($bytes, 0, $bytes.Length) }
        finally { $outStream.Dispose() }
    }
} catch {
    if ($destination) { $destination.Dispose(); $destination = $null }
    if (Test-Path -LiteralPath $outputPath) { Remove-Item -LiteralPath $outputPath }
    throw
} finally {
    if ($destination) { $destination.Dispose() }
    if ($source) { $source.Dispose() }
}

Write-Output "Created $outputPath"
Write-Output "DICOM instances re-keyed: $($dicomEntries.Count)"
Write-Output "New StudyInstanceUID: $studyUid"
Write-Output "Series UIDs re-keyed: $($seriesUidMap.Count)"
