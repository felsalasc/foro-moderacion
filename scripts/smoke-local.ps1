param(
    [string]$UsuariosPath = (Join-Path $PSScriptRoot '../../../SEMANA 2/foro-usuarios'),
    [string]$ContenidoPath = (Join-Path $PSScriptRoot '../../../SEMANA 2/foro-contenido'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '../../.verification')
)
$ErrorActionPreference='Stop'
$taskModeracionPath=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskUsuariosPath=(Resolve-Path -LiteralPath $UsuariosPath).Path
$taskContenidoPath=(Resolve-Path -LiteralPath $ContenidoPath).Path
New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
$taskOutput=(Resolve-Path -LiteralPath $OutputPath).Path
$taskRun=[Guid]::NewGuid().ToString('N')
$taskProcesos=[Collections.Generic.List[Diagnostics.Process]]::new()
$taskJava=(Get-Command java -ErrorAction Stop).Source
$taskListeners=@()
try {
    foreach($taskIndex in 1..3) {
        $taskListener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
        $taskListener.Start(); $taskListeners+=,$taskListener
    }
    $taskPuertos=@($taskListeners | ForEach-Object { $_.LocalEndpoint.Port })
} finally { foreach($taskListener in $taskListeners) { $taskListener.Stop() } }
$taskUsuariosUrl='http://127.0.0.1:'+$taskPuertos[0]
$taskContenidoUrl='http://127.0.0.1:'+$taskPuertos[1]
$taskModeracionUrl='http://127.0.0.1:'+$taskPuertos[2]
$taskJwt=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$taskPassword='Smoke!'+[Guid]::NewGuid().ToString('N')
$taskDb=Join-Path $taskOutput ('moderacion-'+$taskRun)

function Start-Foro([string]$root,[string]$name,[int]$port,[hashtable]$variables,[string[]]$extra=@()) {
    $jar=Join-Path $root ('target/'+$name+'-0.0.1-SNAPSHOT.jar')
    if(-not(Test-Path -LiteralPath $jar)) { throw "Falta compilar $name con mvnw verify" }
    $info=[Diagnostics.ProcessStartInfo]::new($taskJava)
    $info.WorkingDirectory=$root; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    # No heredar perfiles, credenciales Oracle ni puertos de una sesión externa.
    foreach($key in @($info.Environment.Keys)) {
        if($key -match '^(SPRING_|ORACLE_|BOOTSTRAP_|JWT_|USUARIOS_|MODERACION_|CONTENIDO_|PORT$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$)') { $info.Environment.Remove($key) | Out-Null }
    }
    foreach($key in $variables.Keys) { $info.Environment[$key]=[string]$variables[$key] }
    foreach($argument in @('-jar',$jar,'--spring.profiles.active=local',"--server.port=$port")+$extra) { $info.ArgumentList.Add($argument) }
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$info
    $null=$process.Start(); $taskProcesos.Add($process)
    $out=$process.StandardOutput.ReadToEndAsync(); $err=$process.StandardError.ReadToEndAsync()
    $process | Add-Member -NotePropertyName LogOut -NotePropertyValue $out
    $process | Add-Member -NotePropertyName LogErr -NotePropertyValue $err
    $process | Add-Member -NotePropertyName LogName -NotePropertyValue ($name+'-'+$process.Id)
    return $process
}
function Stop-Foro($process) {
    if(-not $process.HasExited) { $process.Kill($true); $process.WaitForExit(10000) | Out-Null }
    if($process.HasExited) {
        [IO.File]::WriteAllText((Join-Path $taskOutput ($process.LogName+'.log')),$process.LogOut.GetAwaiter().GetResult()+$process.LogErr.GetAwaiter().GetResult())
    }
}
function Wait-Foro([string]$url,$process) {
    $deadline=[DateTime]::UtcNow.AddSeconds(60)
    while([DateTime]::UtcNow -lt $deadline) {
        if($process.HasExited) { throw 'Un servicio terminó antes de iniciar; revisar logs locales' }
        try {
            $response=Invoke-WebRequest -Uri $url -SkipHttpErrorCheck -TimeoutSec 2
            if($response.StatusCode -eq 401) { return }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    throw 'El servicio no inició a tiempo'
}
function Request-Foro([string]$method,[string]$url,[string]$token,[object]$body,[int]$expected) {
    $arguments=@{Method=$method; Uri=$url; SkipHttpErrorCheck=$true; TimeoutSec=15}
    if($token) { $arguments.Headers=@{Authorization='Bearer '+$token} }
    if($null -ne $body) {
        $arguments.ContentType='application/json; charset=utf-8'
        $arguments.Body=[Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 10 -Compress))
    }
    $response=Invoke-WebRequest @arguments
    if([int]$response.StatusCode -ne $expected) { throw "HTTP $($response.StatusCode), esperado $expected en $method $url" }
    if($response.Content) { return ($response.Content | ConvertFrom-Json) }
}
try {
    $taskUsuarios=Start-Foro $taskUsuariosPath 'foro-usuarios' $taskPuertos[0] @{
        JWT_SECRET_BASE64=$taskJwt; BOOTSTRAP_ADMIN_ENABLED='true'; BOOTSTRAP_ADMIN_NOMBRE='Admin smoke';
        BOOTSTRAP_ADMIN_CORREO='admin-smoke@example.test'; BOOTSTRAP_ADMIN_PASSWORD=$taskPassword
    }
    $taskContenido=Start-Foro $taskContenidoPath 'foro-contenido' $taskPuertos[1] @{USUARIOS_BASE_URL=$taskUsuariosUrl;MODERACION_BASE_URL=$taskModeracionUrl}
    $taskModeracionVars=@{USUARIOS_BASE_URL=$taskUsuariosUrl;CONTENIDO_BASE_URL=$taskContenidoUrl}
    $taskExtra=@(('--spring.datasource.url=jdbc:h2:file:'+($taskDb.Replace('\','/'))+';MODE=Oracle;WRITE_DELAY=0'),'--spring.jpa.hibernate.ddl-auto=update')
    $taskModeracion=Start-Foro $taskModeracionPath 'foro-moderacion' $taskPuertos[2] $taskModeracionVars $taskExtra
    Wait-Foro $taskUsuariosUrl $taskUsuarios; Wait-Foro $taskContenidoUrl $taskContenido; Wait-Foro $taskModeracionUrl $taskModeracion
    $taskAdmin=(Request-Foro POST "$taskUsuariosUrl/api/auth/login" '' @{correo='admin-smoke@example.test';password=$taskPassword} 200).accessToken
    $null=Request-Foro POST "$taskUsuariosUrl/api/auth/registro" '' @{nombre='Autor smoke';correo='autor-smoke@example.test';password=$taskPassword} 201
    $taskAutor=(Request-Foro POST "$taskUsuariosUrl/api/auth/login" '' @{correo='autor-smoke@example.test';password=$taskPassword} 200).accessToken
    $null=Request-Foro POST "$taskUsuariosUrl/api/auth/registro" '' @{nombre='Lector smoke';correo='lector-smoke@example.test';password=$taskPassword} 201
    $taskLector=(Request-Foro POST "$taskUsuariosUrl/api/auth/login" '' @{correo='lector-smoke@example.test';password=$taskPassword} 200).accessToken
    $taskCategoria=(Request-Foro POST "$taskContenidoUrl/api/categorias" $taskAdmin @{nombre='Smoke'} 201).id
    $taskTema=(Request-Foro POST "$taskContenidoUrl/api/temas" $taskAutor @{titulo='Prueba moderación';contenido='Conservar texto';categoriaId=$taskCategoria} 201).id
    $taskComentario=(Request-Foro POST "$taskContenidoUrl/api/temas/$taskTema/comentarios" $taskAutor @{contenido='Conservar comentario'} 201).id
    $null=Request-Foro PUT "$taskModeracionUrl/api/moderacion/temas/$taskTema" $taskLector @{baneado=$true;motivo='No autorizado'} 403
    $null=Request-Foro PUT "$taskModeracionUrl/api/moderacion/comentarios/$taskComentario" $taskAdmin @{baneado=$true;motivo='Incumple normas'} 200
    $null=Request-Foro GET "$taskContenidoUrl/api/comentarios/$taskComentario" $taskLector $null 404
    $null=Request-Foro DELETE "$taskContenidoUrl/api/temas/$taskTema" $taskAutor $null 409
    $null=Request-Foro PUT "$taskModeracionUrl/api/moderacion/temas/$taskTema" $taskAdmin @{baneado=$true;motivo='Spam'} 200
    $null=Request-Foro GET "$taskContenidoUrl/api/temas/$taskTema" $taskLector $null 404
    $taskLista=@(Request-Foro GET "$taskContenidoUrl/api/temas" $taskLector $null 200)
    if(@($taskLista | Where-Object id -eq $taskTema).Count -ne 0) { throw 'Tema baneado visible en listado' }
    $null=Request-Foro DELETE "$taskContenidoUrl/api/temas/$taskTema" $taskAutor $null 404
    $null=Request-Foro DELETE "$taskContenidoUrl/api/temas/$taskTema" $taskLector $null 404
    $null=Request-Foro PUT "$taskContenidoUrl/api/comentarios/$taskComentario" $taskLector @{contenido='Intento ajeno bloqueado'} 404
    $null=Request-Foro POST "$taskContenidoUrl/api/temas/$taskTema/comentarios" $taskAutor @{contenido='Intento bloqueado'} 404
    # Reiniciar moderación con la MISMA base de archivo prueba persistencia real en H2.
    Stop-Foro $taskModeracion
    $null=Request-Foro GET "$taskContenidoUrl/api/temas/$taskTema" $taskLector $null 503
    $taskModeracion=Start-Foro $taskModeracionPath 'foro-moderacion' $taskPuertos[2] $taskModeracionVars $taskExtra
    Wait-Foro $taskModeracionUrl $taskModeracion
    $null=Request-Foro GET "$taskContenidoUrl/api/temas/$taskTema" $taskLector $null 404
    $taskDecisiones=@(Request-Foro GET "$taskModeracionUrl/api/moderacion/decisiones" $taskAdmin $null 200)
    if($taskDecisiones.Count -ne 2) { throw 'No se conservaron las decisiones tras reiniciar' }
    $null=Request-Foro PUT "$taskModeracionUrl/api/moderacion/temas/$taskTema" $taskAdmin @{baneado=$false;motivo='Restaurado'} 200
    $null=Request-Foro GET "$taskContenidoUrl/api/temas/$taskTema" $taskLector $null 200
    $null=Request-Foro GET "$taskContenidoUrl/api/comentarios/$taskComentario" $taskLector $null 404
    $null=Request-Foro PUT "$taskModeracionUrl/api/moderacion/comentarios/$taskComentario" $taskAdmin @{baneado=$false;motivo='Restaurado'} 200
    $taskRespuesta=Request-Foro GET "$taskContenidoUrl/api/comentarios/$taskComentario" $taskLector $null 200
    if($taskRespuesta.contenido -ne 'Conservar comentario') { throw 'Se perdieron datos al restaurar' }
    $taskResumen=@{resultado='PASS'; servicios=3; persistencia='H2 archivo tras reinicio'; oracleVerificado=$false; fechaUtc=[DateTime]::UtcNow.ToString('o')}
    $taskResumen | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'smoke-result.json') -Encoding utf8
    Write-Output 'PASS: tres servicios reales, permisos, baneo, bloqueo, restauración, 503 y persistencia tras reinicio.'
} finally {
    foreach($taskProcess in $taskProcesos) { Stop-Foro $taskProcess; $taskProcess.Dispose() }
}
