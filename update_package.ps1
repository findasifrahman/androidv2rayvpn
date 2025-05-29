$files = Get-ChildItem -Path "app/src/main/java/com/globlink/vpn" -Recurse -File -Filter "*.kt"
foreach ($file in $files) {
    (Get-Content $file.FullName) |
        ForEach-Object { $_ -replace "package com.v2ray.ang", "package com.globlink.vpn" } |
        ForEach-Object { $_ -replace "import com.v2ray.ang", "import com.globlink.vpn" } |
        Set-Content $file.FullName
}

# Update layout files
$layoutFiles = Get-ChildItem -Path "app/src/main/res/layout" -Recurse -File -Filter "*.xml"
foreach ($file in $layoutFiles) {
    (Get-Content $file.FullName) |
        ForEach-Object { $_ -replace "com.v2ray.ang", "com.globlink.vpn" } |
        Set-Content $file.FullName
}

# Update test files
$testFiles = Get-ChildItem -Path "app/src/test/java/com/v2ray/ang" -Recurse -File -Filter "*.kt"
foreach ($file in $testFiles) {
    (Get-Content $file.FullName) |
        ForEach-Object { $_ -replace "package com.v2ray.ang", "package com.globlink.vpn" } |
        ForEach-Object { $_ -replace "import com.v2ray.ang", "import com.globlink.vpn" } |
        Set-Content $file.FullName
} 