Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root "src\main\resources\assets\scriptautoce\icon.png"
$scale = 8
$pixelSize = 16

$bitmap = [System.Drawing.Bitmap]::new($pixelSize * $scale, $pixelSize * $scale,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::Transparent)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

function Paint([int] $x, [int] $y, [int] $width, [int] $height, [string] $hex) {
    $color = [System.Drawing.ColorTranslator]::FromHtml($hex)
    $brush = [System.Drawing.SolidBrush]::new($color)
    try {
        $graphics.FillRectangle($brush, $x * $scale, $y * $scale, $width * $scale, $height * $scale)
    } finally {
        $brush.Dispose()
    }
}

# Rounded pixel silhouette.
Paint 2 0 12 1 "#0A1524"
Paint 1 1 14 1 "#0A1524"
Paint 0 2 1 12 "#0A1524"
Paint 1 14 14 1 "#0A1524"
Paint 0 2 16 12 "#0A1524"
Paint 1 1 14 13 "#244C66"
Paint 2 1 12 13 "#17364B"
Paint 1 2 14 12 "#17364B"

# Command terminal.
Paint 2 2 12 11 "#071118"
Paint 3 3 10 1 "#27D3C2"
Paint 3 11 10 1 "#166F73"
Paint 2 3 1 8 "#27D3C2"
Paint 13 3 1 8 "#166F73"

# Script sheet with folded corner and command mark.
Paint 4 3 5 8 "#E8C879"
Paint 5 4 4 7 "#F8E9B8"
Paint 7 3 2 2 "#C5964C"
Paint 5 5 3 1 "#315061"
Paint 5 7 2 1 "#315061"
Paint 5 9 1 1 "#14B8A6"
Paint 6 9 2 1 "#315061"

# Automation loop and gear.
Paint 10 3 2 1 "#FF9F43"
Paint 12 4 1 2 "#FF9F43"
Paint 12 6 1 2 "#FF9F43"
Paint 11 8 1 1 "#FF9F43"
Paint 10 8 1 1 "#59F0D1"
Paint 9 7 1 1 "#FF9F43"
Paint 9 5 1 2 "#FF9F43"
Paint 11 3 1 1 "#FFF2C7"
Paint 9 8 1 1 "#FFF2C7"

Paint 10 10 3 2 "#0C2730"
Paint 9 10 1 1 "#6CF2A5"
Paint 13 10 1 1 "#6CF2A5"
Paint 11 9 1 1 "#6CF2A5"
Paint 11 12 1 1 "#6CF2A5"
Paint 11 10 1 2 "#8CFFB8"

# Bottom execution indicator.
Paint 3 13 4 1 "#6CF2A5"
Paint 8 13 2 1 "#27D3C2"
Paint 11 13 2 1 "#244C66"

[System.IO.Directory]::CreateDirectory((Split-Path -Parent $output)) | Out-Null
$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()

Write-Output "Generated $output"
