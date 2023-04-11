@ECHO OFF

setlocal

FOR /F "tokens=1,2 delims==" %%G IN (system.txt) DO (set %%G=%%H)  

if exist target\ (
  set JARDIR=target
) else (
  set JARDIR=.
)

IF "%1"=="list"         (SET list=true)
IF "%1"=="delete"       (SET delete=true)
IF "%1"=="view"         (SET view=true)
IF "%1"=="connect"      (SET connect=true)
IF "%1"=="prepare"      (SET prepare=true)
IF "%1"=="disconnect"   (SET disconnect=true)
IF "%1"=="ssh-keygen"   (SET sshkeygen=true)
IF "%1"=="x509-keygen"  (SET x509keygen=true)
IF "%1"=="ssh-upload"   (SET sshupload=true)
IF "%1"=="x509-upload"  (SET x509upload=true)
IF "%1"=="x509-getcert" (SET x509getcert=true)
IF "%1"=="ssh-test"     (SET sshtest=true)
IF "%1"=="x509-ghsign"  (SET x509ghsign=true)
IF "%1"=="gh-auth"      (SET ghauth=true)
IF "%1"=="sign-open-requests" (SET signopenrequests=true)

IF "%list%"=="true" (
  ECHO LIST
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-list-issues %ISSUES% %2
  ::gh issue list --repo %ISSUES%
  goto :eof
)

IF "%delete%"=="true" (
  ECHO DELETE
  :: for some reason, the Java library does not have a method to delete an issue (maybe not in the REST API)
  gh issue delete --repo %ISSUES% "%2"
  goto :eof
)

IF "%view%"=="true" (
  ECHO VIEW
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-view-issue %ISSUES% %2
  ::gh issue view --repo %ISSUES% "%2"
  goto :eof
)

IF "%x509ghsign%"=="true" (
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar sign-from-issues %ISSUES%
  goto :eof
)

IF "%ghauth%"=="true" (
  if "%~2"=="" (
    ECHO gh-auth TOKEN
    goto :eof
  )
  ECHO %2>gh-token.txt
  ECHO Y | cacls gh-token.txt /P %COMPUTERNAME%\%USERNAME%:F
  ::java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-auth %2
  goto :eof
)


SET REALM=%2
SET SYSTEM=%3
SET DEVICE=%4

IF "%prepare%"=="true" (
  ECHO %DEVICE%.%SYSTEM%.%REALM%

  DEL properties.txt
  echo REALM=%REALM%> properties.txt
  echo SYSTEM=%SYSTEM%>> properties.txt
  echo DEVICE=%DEVICE%>> properties.txt
  echo broker=%BROKER%>> properties.txt
  echo control=%REALM%/%SYSTEM%/%DEVICE%/tunnel/control>> properties.txt
  echo state=%REALM%/%SYSTEM%/%DEVICE%/tunnel/state>> properties.txt
  echo clientId=%DEVICE%.%SYSTEM%.%REALM%%CLIENTID_SUFFIX%>> properties.txt
  echo shortId=%DEVICE%.%SYSTEM%.%REALM%>> properties.txt
  echo certificate=%DEVICE%.%SYSTEM%.%REALM%.x509.cert>> properties.txt
  echo certificateRequest=%DEVICE%.%SYSTEM%.%REALM%.x509.csr>> properties.txt
  echo privateKey=%DEVICE%.%SYSTEM%.%REALM%.x509.key>> properties.txt
  echo sshPrivateKey=%DEVICE%.%SYSTEM%.%REALM%.sshkey>> properties.txt
  echo sshPublicKey=%DEVICE%.%SYSTEM%.%REALM%.sshkey.pub>> properties.txt
  echo sshProxyHost=%PROXY%>> properties.txt
  echo sshProxyUser=%SYSTEM%>> properties.txt
  echo sshProxyKey=%DEVICE%.%SYSTEM%.%REALM%.sshkey>> properties.txt
  echo sshProxyPort=-1>> properties.txt
  goto :eof
)

FOR /F "tokens=1,2 delims==" %%G IN (properties.txt) DO (set %%G=%%H)  
echo Running for device %DEVICE%.%SYSTEM%.%REALM%

set TARGET=%2
IF "%connect%"=="true" (
  ECHO CONNECT
  if "%~2"=="" (
    ECHO Missing argument: connect TARGET
    goto :eof
  )
  ::java -jar jars/sivantoledo-iot.jar connect %TARGET% %REALM% %sshPrivateKey%
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar connect %TARGET% %REALM% %sshPrivateKey%
  goto :eof
)

IF "%disconnect%"=="true" (
  ECHO CONNECT
  if "%~2"=="" (
    ECHO Missing argument: connect TARGET
    goto :eof
  )
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar disconnect %TARGET%
  goto :eof
)

IF "%sshtest%"=="true" (
  echo ssh %SYSTEM%@%% -i %sshPrivateKey% whoami
  ssh %SYSTEM%@%PROXY% -i %sshPrivateKey% whoami
  goto :eof
)

IF "%sshkeygen%"=="true" (
  ssh-keygen -N "" -f %sshPrivateKey% -C %shortId%
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue %ISSUES% %sshPublicKey%                                                                         
  ::gh issue create --repo %ISSUES% --body-file %sshPublicKey% --title %sshPublicKey%
  ::ECHO Uploaded %sshPublicKey%
  goto :eof
)

IF "%sshupload%"=="true" (
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue %ISSUES% %sshPublicKey%                                                                         
  ::gh issue create --repo %ISSUES% --body-file %sshPublicKey% --title %sshPublicKey%
  ::ECHO Uploaded %sshPublicKey%
  goto :eof
)

IF "%x509keygen%"=="true" (
  "\Program Files\FireDaemon OpenSSL 3\bin\openssl.exe" req -subj /CN=%clientId%/OU=%DEVICE%/O=%SYSTEM% -newkey rsa:4096 -keyout %privateKey% -nodes -out %certificateRequest% -verbose
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue %ISSUES% %certificateRequest%                                                                         
  ::gh issue create --repo %ISSUES% --body-file %certificateRequest% --title %certificateRequest%
  ::ECHO Uploaded %certificateRequest%
  goto :eof
)

IF "%x509upload%"=="true" (
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue %ISSUES% %certificateRequest%                                                                         
  ::gh issue create --repo %ISSUES% --body-file %certificateRequest% --title %certificateRequest%
  ::ECHO Uploaded %certificateRequest%
  goto :eof
)

IF "%x509getcert%"=="true" (
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar gh-get-issue %ISSUES% %certificate%                                                                         
  ::echo Downloaded %certificate% 
  goto :eof
)

ECHO Command "%1" not known
