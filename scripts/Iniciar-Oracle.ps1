# Compatible con Windows PowerShell 5.1 y PowerShell 7. No almacena claves en disco.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Usuarios','Contenido','Moderacion')]
    [string]$Servicio,
    [string]$WalletPath = 'C:\Wallet_BIBLIOTECA',
    [ValidatePattern('^[a-zA-Z0-9_.-]+$')]
    [string]$Alias = 'biblioteca_low',
    [string]$JavaPath,
    [ValidateRange(30000,1800000)]
    [int]$Keepalive = 30000,
    [switch]$ValidarSolamente,
    [switch]$ProbarConexion
)

$ErrorActionPreference='Stop'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$taskProyectos=@{
    Usuarios=@{Carpeta='SEMANA 2/foro-usuarios';Nombre='foro-usuarios';Esquema='FORO_USUARIOS';Puerto=8081}
    Contenido=@{Carpeta='SEMANA 2/foro-contenido';Nombre='foro-contenido';Esquema='FORO_CONTENIDO';Puerto=8082}
    Moderacion=@{Carpeta='SEMANA 5/foro-moderacion';Nombre='foro-moderacion';Esquema='FORO_MODERACION';Puerto=8083}
}
$taskProyecto=$taskProyectos[$Servicio]
$taskCarpeta=Join-Path $taskWorkspace $taskProyecto.Carpeta
$taskJar=Join-Path $taskCarpeta ('target/'+$taskProyecto.Nombre+'-0.0.1-SNAPSHOT.jar')
if(-not $JavaPath) {
    $JavaPath=Join-Path $taskWorkspace 'BIBLIOTECA/.tools/jdk17/jdk-17.0.20.1+1/bin/java.exe'
}
foreach($taskArchivo in @($JavaPath,$taskJar,(Join-Path $WalletPath 'tnsnames.ora'))) {
    if(-not(Test-Path -LiteralPath $taskArchivo -PathType Leaf)) { throw ('No existe: '+$taskArchivo) }
}
$taskTns=[IO.File]::ReadAllText((Join-Path $WalletPath 'tnsnames.ora'))
if($taskTns -notmatch ('(?mi)^\s*'+[regex]::Escape($Alias)+'\s*=')) {
    throw 'El alias indicado no aparece en tnsnames.ora.'
}
if($ValidarSolamente) {
    [pscustomobject]@{Servicio=$Servicio;Esquema=$taskProyecto.Esquema;Puerto=$taskProyecto.Puerto;Java=$JavaPath;Jar=$taskJar;Wallet=$WalletPath;Alias=$Alias}
    return
}
if(-not $ProbarConexion -and (Get-NetTCPConnection -LocalPort $taskProyecto.Puerto -State Listen -ErrorAction SilentlyContinue)) {
    throw ('El puerto '+$taskProyecto.Puerto+' ya esta ocupado. No se iniciara otra instancia.')
}

$taskLogDir=Join-Path $taskWorkspace 'SEMANA 5/.verification/oracle'
New-Item -ItemType Directory -Path $taskLogDir -Force | Out-Null
$taskLog=Join-Path $taskLogDir ($taskProyecto.Nombre+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log')
$taskVariables=@('ORACLE_URL','ORACLE_USERNAME','ORACLE_PASSWORD','PORT','USUARIOS_BASE_URL','CONTENIDO_BASE_URL','MODERACION_BASE_URL')
$taskAnterior=@{}
foreach($taskVariable in $taskVariables) { $taskAnterior[$taskVariable]=[Environment]::GetEnvironmentVariable($taskVariable,'Process') }
$taskClave=$null
$taskCredencial=$null
$taskRng=$null
try {
    Write-Host ('Servicio: '+$Servicio+' | esquema: '+$taskProyecto.Esquema+' | puerto: '+$taskProyecto.Puerto)
    $taskClave=Read-Host ('Contrasena de '+$taskProyecto.Esquema+', sin comillas') -AsSecureString
    if($taskClave.Length -eq 0) { throw 'La contrasena no puede estar vacia.' }
    $taskCredencial=New-Object System.Net.NetworkCredential -ArgumentList @('', $taskClave)
    $env:ORACLE_URL='jdbc:oracle:thin:@'+$Alias+'?TNS_ADMIN='+([IO.Path]::GetFullPath($WalletPath).Replace('\','/'))
    $env:ORACLE_USERNAME=$taskProyecto.Esquema
    $env:ORACLE_PASSWORD=$taskCredencial.Password
    $env:PORT=[string]$taskProyecto.Puerto
    $env:USUARIOS_BASE_URL='http://localhost:8081'
    $env:CONTENIDO_BASE_URL='http://localhost:8082'
    $env:MODERACION_BASE_URL='http://localhost:8083'
    if($Servicio -eq 'Usuarios' -and -not $env:JWT_SECRET_BASE64) {
        $taskBytes=New-Object byte[] 32
        $taskRng=[Security.Cryptography.RandomNumberGenerator]::Create()
        $taskRng.GetBytes($taskBytes)
        $env:JWT_SECRET_BASE64=[Convert]::ToBase64String($taskBytes)
        # Conservar en ESTA terminal para reinicios; no escribir ni mostrar la clave.
        [Array]::Clear($taskBytes,0,$taskBytes.Length)
    }
    Write-Host ('Registro de este intento: '+$taskLog)
    if($ProbarConexion) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $taskLibDir=Join-Path $taskLogDir ('jdbc-'+[Guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $taskLibDir -Force | Out-Null
        $taskZip=[IO.Compression.ZipFile]::OpenRead($taskJar)
        try {
            foreach($taskEntry in $taskZip.Entries) {
                if($taskEntry.FullName -match '^BOOT-INF/lib/(ojdbc\d+|oraclepki|osdt_core|osdt_cert)-[^/]+\.jar$') {
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,(Join-Path $taskLibDir $taskEntry.Name))
                }
            }
        } finally { $taskZip.Dispose() }
        Write-Host 'Prueba JDBC sin pool; puede durar unos 2 minutos. No modifica datos.'
        $ErrorActionPreference='Continue'
        & $JavaPath -cp ($taskLibDir+'/*') (Join-Path $PSScriptRoot 'ProbarOracle.java') 2>&1 |
            Tee-Object -FilePath $taskLog
        if($LASTEXITCODE -ne 0) { Write-Warning ('Diagnostico no aprobado. Revisar: '+$taskLog) }
        return
    }
    Write-Host 'Tras Started ... el servicio queda esperando solicitudes. Deja esta ventana abierta.'
    Write-Host ('Keepalive (ms): '+$Keepalive+'. Sigue siendo una prueba; el arranque no acredita estabilidad de Oracle.')
    # PowerShell 5.1 trata stderr nativo como ErrorRecord: no abortar ante un aviso de Java.
    $ErrorActionPreference='Continue'
    & $JavaPath -jar $taskJar --spring.profiles.active=oracle ('--spring.datasource.hikari.keepalive-time='+$Keepalive) 2>&1 |
        Tee-Object -FilePath $taskLog
    $taskExitCode=$LASTEXITCODE
    if($taskExitCode -ne 0) { Write-Warning ('Java termino con codigo '+$taskExitCode+'. Revisar el registro indicado.') }
}
finally {
    foreach($taskVariable in $taskVariables) { [Environment]::SetEnvironmentVariable($taskVariable,$taskAnterior[$taskVariable],'Process') }
    if($taskClave) { $taskClave.Dispose() }
    if($taskRng) { $taskRng.Dispose() }
    $taskCredencial=$null
}
