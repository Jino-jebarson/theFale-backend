try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/otp/send" -Method Post -ContentType "application/json" -Body '{"phone":"9876543210","role":"ROLE_CANDIDATE"}'
    $res | ConvertTo-Json
} catch {
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host "Error Body: " $reader.ReadToEnd()
}

