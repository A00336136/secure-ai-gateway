/**
 * Login helper — reusable JavaScript function for Karate feature files
 *
 * Usage in .feature files:
 *   * def loginResponse = call read('login-helper.js') { username: '#(user.username)', password: '#(user.password)', baseUrl: '#(baseUrl)' }
 *   * def authToken = loginResponse.token
 *
 * Returns: { token, tokenType, username, role, expiresIn }
 */
function fn(args) {
    var baseUrl = args.baseUrl || 'http://localhost:8100';
    var username = args.username;
    var password = args.password;

    var response = karate.call('classpath:karate-call-login.js', {
        url: baseUrl + '/auth/login',
        username: username,
        password: password
    });

    return response;
}
