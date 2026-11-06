# Test End-to-End Mentor Sessions Flow

# 1. Login as Jino (accredited mentor)
$authBody = @{
    contact = "+919876543210"
    otp = "123456"
} | ConvertTo-Json

$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/verify-otp" -Method Post -ContentType "application/json" -Body $authBody
$mentorToken = $loginRes.data.token
Write-Host "Mentor Login Token obtained." -ForegroundColor Green

# 2. Check Mentor Status
$headers = @{
    "Authorization" = "Bearer $mentorToken"
    "Content-Type" = "application/json"
}

$statusRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/huddle/mentor/status" -Method Get -Headers $headers
Write-Host "Mentor Status: $($statusRes.data.status)" -ForegroundColor Cyan

# 3. Create a Clinical Mentorship Session as Mentor
$sessionPayload = @{
    title = "ICU Ventilator & Weaning Masterclass"
    category = "Clinical Masterclass"
    when = (Get-Date).AddDays(5).ToString("yyyy-MM-ddTHH:mm:ssZ")
    where = "Live Interactive (Google Meet)"
    seats = 30
    description = "Hands-on simulated lab on weaning criteria, high-flow nasal cannula management, and bedside mobilization."
    forRoles = @("Nursing", "Doctors", "Interns")
} | ConvertTo-Json

$createRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/huddle/sessions" -Method Post -Headers $headers -Body $sessionPayload
$sessionId = $createRes.data.id
Write-Host "Session Created successfully! ID: $sessionId" -ForegroundColor Green
Write-Host "Host Name: $($createRes.data.hostName)" -ForegroundColor Yellow
Write-Host "Host Sub: $($createRes.data.hostSub)" -ForegroundColor Yellow
Write-Host "Seats: $($createRes.data.seats)" -ForegroundColor Yellow
Write-Host "Is Host: $($createRes.data.isHost)" -ForegroundColor Yellow

# 4. Fetch all sessions
$sessionsRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/huddle/sessions" -Method Get -Headers $headers
Write-Host "Total Sessions returned: $($sessionsRes.data.Count)" -ForegroundColor Green

# 5. Book seat in session
$bookRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/huddle/sessions/$sessionId/book" -Method Post -Headers $headers
Write-Host "Booked seat. Booked Count: $($bookRes.data.bookedCount), isBooked: $($bookRes.data.isBooked)" -ForegroundColor Green

# 6. Cancel booking
$cancelRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/huddle/sessions/$sessionId/cancel" -Method Post -Headers $headers
Write-Host "Booking cancelled. Booked Count: $($cancelRes.data.bookedCount), isBooked: $($cancelRes.data.isBooked)" -ForegroundColor Cyan

