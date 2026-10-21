param([string]$PackPath = "$PSScriptRoot/../dist/fartmc-sounds.zip")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $PackPath).Path)
try {
    function Read-FoodEntry([string]$name) {
        $entry = $archive.GetEntry($name)
        if (!$entry) { throw "Missing food asset: $name" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    $manifest = Read-FoodEntry 'FOOD_PROVENANCE.json' | ConvertFrom-Json
    if ($manifest.files.Count -ne 9) { throw 'Wrong food asset count.' }
    foreach ($asset in $manifest.files) {
        $entry = $archive.GetEntry($asset.path)
        if (!$entry) { throw "Missing $($asset.path)" }
        $stream = $entry.Open()
        $hash = [Security.Cryptography.SHA256]::Create()
        try {
            $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
            if ($actual -cne $asset.sha256) { throw "Food asset hash mismatch: $($asset.path)" }
        } finally { $stream.Dispose(); $hash.Dispose() }
    }
    foreach ($id in @('belyash_station','khacheburek','poisoned_cheburek')) {
        $item = Read-FoodEntry "assets/fartmc/items/$id.json" | ConvertFrom-Json
        if ($item.model.type -ne 'minecraft:model' -or $item.model.model -cne "fartmc:item/$id") { throw "Invalid item model: $id" }
        $model = Read-FoodEntry "assets/fartmc/models/item/$id.json" | ConvertFrom-Json
        if ($model.elements.Count -lt 1 -or $model.textures.'0' -cne "fartmc:item/$id") { throw "Invalid geometry: $id" }
        foreach ($view in @('gui','ground','fixed','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand')) {
            if (!$model.display.$view) { throw "Missing display transform $id / $view" }
        }
        foreach ($element in $model.elements) {
            foreach ($value in @($element.from) + @($element.to)) {
                if ($value -lt -16 -or $value -gt 32) { throw "Invalid model bounds: $id" }
            }
            foreach ($face in $element.faces.PSObject.Properties.Value) {
                if ($face.texture -ne '#0' -or $face.uv.Count -ne 4) { throw "Invalid face: $id" }
                foreach ($uv in $face.uv) { if ($uv -lt 0 -or $uv -gt 16) { throw "UV outside texture: $id" } }
            }
        }
    }
    if ($archive.Entries | Where-Object FullName -Like 'assets/minecraft/*') { throw 'Food pack must not replace vanilla models.' }
    Write-Output 'FOOD PACK PASS: three 3D models, item definitions, texture hashes, UVs and seven display transforms each.'
} finally { $archive.Dispose() }
