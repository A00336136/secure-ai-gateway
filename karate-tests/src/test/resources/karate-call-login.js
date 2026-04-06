/**
 * Low-level login helper using Karate's HTTP client
 * Called via: call read('classpath:karate-call-login.js') { url, username, password }
 */
function fn(args) {
    var config = {
        url: args.url,
        method: 'post',
        body: { username: args.username, password: args.password },
        headers: { 'Content-Type': 'application/json' }
    };
    var response = karate.http(config.url)
        .contentType('application/json')
        .post(config.body);
    return response.body;
}
