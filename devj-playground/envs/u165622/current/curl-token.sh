#! sh

#user=U123456
user=YOUR_USER
curl --no-progress-meter -k -X POST -d "grant_type=client_credentials" -u $user -H "Content-Type: application/x-www-form-urlencoded" https://employee.login.int.signal-iduna.org/auth/token
