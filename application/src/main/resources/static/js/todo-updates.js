let stompClient = null;

function connectToWebSocketEndpoint(email, csrfHeaderName, csrfTokenValue) {
  const socket = new SockJS('/websocket');

  const headers = {};
  headers[csrfHeaderName] = csrfTokenValue;
  headers['X-CSRF-TOKEN'] = csrfTokenValue;
  stompClient = Stomp.over(socket);
  stompClient.connect(headers, () => {
    console.log("CLIENT SUBSCRIBED");

    stompClient.subscribe('/topic/todoUpdates', function (message) {
      $('#message').html(message.body);
      $('#toast').toast('show');
    });

    if (email) {
      stompClient.subscribe('/topic/todoUpdates/' + email, function (message) {
        $('#message').html(message.body);
        $('#toast').toast('show');
      });
    }
  });
}

function disconnectFromWebSocketEndpoint() {
  if (stompClient !== null) {
    stompClient.disconnect();
  }
}

$(document).ready(function () {
  $('#toast').toast({delay: 10000});
});
