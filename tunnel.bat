@ECHO OFF

setlocal

FOR /F "tokens=1,2 delims==" %%G IN (system.txt) DO (set %%G=%%H)  

if exist target\ (
  set JARDIR=target
) else (
  set JARDIR=.
)

if "%~1"=="" (
    echo tunnel command ...
    echo.
    echo   commands:
    echo     gh-auth TOKEN
    echo     prepare SYSTEM DEVICE
    echo     ssh-keygen
    echo     ssh-upload
    REM echo     ssh-getpub
    echo     x509-keygen
    echo     x509-upload
    echo     x509-getcert
    echo.     
    echo     connect
    echo     disconnect
    echo.     
    echo     list
    echo     delete ISSUE-NUMBER
    echo.     
    echo     ssh-test
    echo.     
    REM echo     listen
    REM echo     
    REM echo     proxy-update-pubs    (on ssh jump host; not for end users)
    REM echo     proxy-prepare-system (on ssh jump host; not for end users)
    REM echo     
    echo     x509-ghsign          (on computer with AWS credentials; not for end users)
    goto :eof
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

SET SYSTEM=%2
SET DEVICE=%3

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
    ECHO Missing argument: connect user@target
    goto :eof
  )
  
  if "%ALLOCATOR%"=="hostname" (
    FOR /F "tokens=1,2 delims=@" %%G IN ("%TARGET%") DO (
      set USER=%%G
      set DEVICE=%%H
    )

   ::echo device !DEVICE!
   ::echo user   !USER!
    
    IF 1!DEVICE! NEQ +1!DEVICE! (
      echo Missing argument: connect user@target
      echo target must be a number
      goto :eof
    )

    IF "x!DEVICE!"=="x" (
      echo Missing argument: connect user@target
      goto :eof
    )

    call set "VARSTART=ALLOCATION_START_%%SYSTEM%%"
    call set "VAREND=ALLOCATION_END_%%SYSTEM%%"

    FOR /F "tokens=1,2 delims==" %%G IN (system.txt) DO (
      ::echo x !START! %%G
      if !VARSTART!==%%G (set START=%%H)
      if !VAREND!==%%G   (set END=%%H)
    )  

    set /A targetPort=!START!+!DEVICE!

    IF !targetPort! GTR !END! (
      echo target port number !targetPort! is larger that system limit !END!
      goto :eof
    )


    echo tartget port is !targetPort!

    
  	ssh ^
	    -o ProxyCommand="ssh -W %%h:%%p %SYSTEM%@%sshProxyHost% -i %sshProxyKey%" ^
            -o StrictHostKeyChecking=no ^
            -o UserKnownHostsFile=NUL ^
            -i %sshPrivateKey% ^
	      !USER!@localhost ^
            -p !targetPort!
    
  ) else (
    ::java -jar jars/sivantoledo-iot.jar connect %TARGET% %REALM% %sshPrivateKey%
    java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar connect %TARGET% %sshPrivateKey%
  )
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

if "%ALLOCATOR%"=="hostname" (
  ECHO Command "%1" not known (or is disconnect or an x509 command, which are not required in this realm)
  goto :eof
)

IF "%disconnect%"=="true" (
  ECHO CONNECT
  if "%~2"=="" (
    ECHO Missing argument: connect TARGET
    goto :eof
  )
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar disconnect %2
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

REM This command does not need properties.txt, but it is here as an x509 command
IF "%x509ghsign%"=="true" (
  java -jar %JARDIR%/sivantoledo.iot-1.0-jar-with-dependencies.jar sign-from-issues %ISSUES%
  goto :eof
)


ECHO Command "%1" not known
