param([string]$BaseUrl='http://127.0.0.1:8080')
$ErrorActionPreference='Stop'
$today=[DateTime]::Today.ToString('yyyy-MM-dd')
$tomorrow=[DateTime]::Today.AddDays(1).ToString('yyyy-MM-dd')
$checks=[Collections.Generic.List[string]]::new()

function Assert-True([bool]$ok,[string]$message){if(-not $ok){throw "断言失败：$message"};$checks.Add($message)}
function Call([string]$method,[string]$path,$session,$body=$null,[string]$csrf=$null,[hashtable]$headers=@{}){
  $params=@{Method=$method;Uri="$BaseUrl$path";SkipHttpErrorCheck=$true}
  if($session){$params.WebSession=$session}
  if($null-ne$body){$params.ContentType='application/json';$params.Body=$body|ConvertTo-Json -Depth 10 -Compress}
  if($csrf){$headers['X-CSRF-Token']=$csrf;$headers['Origin']='http://127.0.0.1:5173'}
  if($headers.Count){$params.Headers=$headers}
  $r=Invoke-WebRequest @params;$j=if($r.Content){$r.Content|ConvertFrom-Json}else{$null}
  [pscustomobject]@{Status=[int]$r.StatusCode;Json=$j}
}
function Login([string]$identity){
  $s=[Microsoft.PowerShell.Commands.WebRequestSession]::new();$r=Call POST '/api/v1/auth/mock-login' $s @{userId=$identity}
  [pscustomobject]@{Session=$s;User=$r.Json.data;Csrf=$r.Json.data.csrfToken;Status=$r.Status}
}

$health=Invoke-RestMethod "$BaseUrl/actuator/health"
Assert-True ($health.status-eq'UP') '健康检查为UP'

$passwordSession=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$passwordLogin=Call POST '/api/v1/auth/password-login' $passwordSession @{username='superadmin';password='Admin123!'}
Assert-True ($passwordLogin.Status-eq200-and$passwordLogin.Json.data.role-eq'SUPER_ADMIN'-and$passwordLogin.Json.data.mustChangePassword) '初始超管密码登录后要求改密'
$beforeChange=Call GET '/api/v1/admin/accounts' $passwordSession
Assert-True ($beforeChange.Status-eq403-and$beforeChange.Json.error.code-eq'PASSWORD_CHANGE_REQUIRED') '强制改密前不能进入业务接口'
$changed=Call POST '/api/v1/auth/change-password' $passwordSession @{currentPassword='Admin123!';newPassword='Admin456789!'} $passwordLogin.Json.data.csrfToken
$newPasswordSession=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
$newPasswordLogin=Call POST '/api/v1/auth/password-login' $newPasswordSession @{username='superadmin';password='Admin456789!'}
Assert-True (($changed.Status -eq 200) -and ($newPasswordLogin.Status -eq 200) -and (-not $newPasswordLogin.Json.data.mustChangePassword)) '首次密码修改后可重新登录'

$streamer=Login 'streamer04'
Assert-True ($streamer.User.role-eq'STREAMER'-and$null-eq$streamer.User.dingTalkUserId) '无钉钉绑定主播可登录'
$ctx=Call GET "/api/v1/booking-context?date=$tomorrow" $streamer.Session
$makeupArtist=$ctx.Json.data.makeupArtists[0].id;$team=$ctx.Json.data.teams[0].id
Assert-True ($ctx.Json.data.rules.leadMinutes-eq20-and$ctx.Json.data.rules.cancelLimit-eq2-and$ctx.Json.data.rules.modifyLimit-eq3) '主播20分钟及2次取消/3次修改规则已下发'

$created=Call POST '/api/v1/appointments' $streamer.Session @{bookingDate=$tomorrow;makeupArtistId=$makeupArtist;teamId=$team;startTime='08:00'} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($created.Status-eq200) '主播创建预约成功'
$modified=Call PATCH "/api/v1/appointments/$($created.Json.data.id)" $streamer.Session @{makeupArtistId=$makeupArtist;teamId=$team;startTime='08:10';version=0} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($modified.Status-eq200-and$modified.Json.data.modifyCount-eq1) '主播修改成功并累计次数'
$cancel1=Call POST "/api/v1/appointments/$($created.Json.data.id)/cancel" $streamer.Session @{version=1} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($cancel1.Status-eq200-and$cancel1.Json.data.cancelCount-eq1) '首次取消释放资格'

$second=Call POST '/api/v1/appointments' $streamer.Session @{bookingDate=$tomorrow;makeupArtistId=$makeupArtist;teamId=$team;startTime='09:00'} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$cancel2=Call POST "/api/v1/appointments/$($second.Json.data.id)/cancel" $streamer.Session @{version=0} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$third=Call POST '/api/v1/appointments' $streamer.Session @{bookingDate=$tomorrow;makeupArtistId=$makeupArtist;teamId=$team;startTime='10:00'} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$cancel3=Call POST "/api/v1/appointments/$($third.Json.data.id)/cancel" $streamer.Session @{version=0} $streamer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($cancel2.Status-eq200-and$third.Status-eq200-and$cancel3.Status-eq409-and$cancel3.Json.error.code-eq'CANCEL_LIMIT_REACHED') '取消两次后仍可重约但不能再自行取消'

$admin=Login 'admin01'
$adminConflict=Call POST '/api/v1/admin/appointments' $admin.Session @{bookingDate=$tomorrow;streamerUserId='streamer03';makeupArtistId=$makeupArtist;teamId=$team;startTime='10:10'} $admin.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($adminConflict.Status-eq200-and$adminConflict.Json.data.conflictOverride) '管理员允许化妆师时段冲突并标记例外'
$void=Call POST "/api/v1/admin/appointments/$($third.Json.data.id)/void" $admin.Session @{version=0} $admin.Csrf
Assert-True ($void.Status-eq404) '作废接口已删除'
$card=Call POST '/api/v1/admin/appointments/schedule-card' $admin.Session @{bookingDate=$tomorrow} $admin.Csrf
Assert-True ($card.Status-eq200) '超管可手动触发安排卡片'
$accounts=Call GET '/api/v1/admin/accounts' $admin.Session
$unbound=$accounts.Json.data|Where-Object{$_.nickname-eq'可可'}|Select-Object -First 1
Assert-True ($null-eq$unbound.warning) '账号列表不再显示已作废的钉钉绑定提醒'

$observer=Login 'observer01'
$observerRead=Call GET "/api/v1/admin/appointments?date=$tomorrow" $observer.Session
$observerWrite=Call POST '/api/v1/admin/appointments' $observer.Session @{bookingDate=$tomorrow;streamerUserId='streamer02';makeupArtistId=$makeupArtist;teamId=$team;startTime='11:00'} $observer.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
Assert-True ($observerRead.Status-eq200-and$observerWrite.Status-eq403) '观察员可读但不可写'

$makeup=Login 'makeup01'
$makeupAccounts=Call GET '/api/v1/admin/accounts' $makeup.Session
$makeupCard=Call POST '/api/v1/admin/appointments/schedule-card' $makeup.Session @{bookingDate=$tomorrow} $makeup.Csrf
Assert-True ($makeupAccounts.Status-eq403-and$makeupCard.Status-eq403) '化妆师仅可查看排期资源并为本人代约'

$makeupArtists=Call GET '/api/v1/admin/makeup-artists' $admin.Session
$teamRows=Call GET '/api/v1/admin/teams' $admin.Session
$activeRoleOrder=(($accounts.Json.data|Where-Object{$_.active}|Select-Object -ExpandProperty role|Select-Object -Unique)-join ',')
Assert-True ($activeRoleOrder-eq'SUPER_ADMIN,OPERATOR,OBSERVER,MAKEUP,STREAMER'-and-not$accounts.Json.data[-1].active) '账号按启用、角色和更新时间排序'
Assert-True (-not$makeupArtists.Json.data[-1].active-and-not$teamRows.Json.data[-1].active) '化妆师和团队按启用及更新时间排序'
$makeupArtistToday=$makeupArtists.Json.data|Where-Object { $_.id -eq '20000000-0000-0000-0000-000000000002' }|Select-Object -First 1
$current=[DateTimeOffset]::Now
$rounded=[DateTime]::Today.AddMinutes([Math]::Ceiling(($current.Hour*60+$current.Minute+2)/10)*10)
$slotTime=$rounded.ToString('HH:mm')
$makeupArtistUpdate=Call PATCH "/api/v1/admin/makeup-artists/$($makeupArtistToday.id)" $admin.Session @{name=$makeupArtistToday.name;avatarUrl=$makeupArtistToday.avatarUrl;workDays=@(1,2,3,4,5,6,7);workStart='00:00';workEnd='23:50';active=$true;attending=$true;version=$makeupArtistToday.version} $admin.Csrf
if($makeupArtistUpdate.Status-ne200){throw "化妆师更新失败：HTTP $($makeupArtistUpdate.Status)，$($makeupArtistUpdate.Json.error.code) $($makeupArtistUpdate.Json.error.message)"}
Assert-True ($makeupArtistUpdate.Status-eq200) '化妆师单段排班和出勤可维护'
$todayBooking=Call POST '/api/v1/admin/appointments' $admin.Session @{bookingDate=$today;streamerUserId='streamer01';makeupArtistId=$makeupArtistToday.id;teamId=$team;startTime=$slotTime} $admin.Csrf @{'Idempotency-Key'=[guid]::NewGuid().ToString()}
$eventTime=[DateTimeOffset]::Now.ToUnixTimeMilliseconds().ToString()
$gate=Call POST "/api/v1/integrations/moredian/recognition-events?orgId=mock-org&signVersion=1&timestamp=$eventTime&nonce=smoke" $null @{callbackTag='REC_SUCCESS';data=@{memberId='streamer01';deviceSn='mock-device';recognizeTime=$eventTime}}
$detail=Call GET "/api/v1/admin/appointments/$($todayBooking.Json.data.id)" $admin.Session
Assert-True ($gate.Status-eq200-and$detail.Json.data.attendanceStatus-eq'ARRIVED') '魔点回调按实际打卡时间更新为已到司'

Write-Host "新版API冒烟通过：$($checks.Count)项"
$checks|ForEach-Object{Write-Host "  PASS $_"}

