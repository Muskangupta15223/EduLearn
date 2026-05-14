const http = require('http');

http.get({
  host: '127.0.0.1',
  port: 8080,
  path: '/courses/4',
  headers: {
    'X-User-Id': '3',
    'X-User-Role': 'ADMIN'
  }
}, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    console.log(`Status: ${res.statusCode}`);
    console.log(`Data: ${data}`);
  });
}).on('error', err => console.log(err));
