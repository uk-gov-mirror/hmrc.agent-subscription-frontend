self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open('agents-offline').then(function (cache) {
            return cache.addAll([
                'offline.html'
            ]);
        })
    );
});

self.addEventListener("fetch", function(event) {
    if (event.request.method !== 'GET') {
        return;
    }

    event.respondWith(

        caches.match(event.request)
        .then(function(cached) {
            var networked = fetch(event.request)
                .then(fetchedFromNetwork, unableToResolve)
                .catch(unableToResolve);
            return cached || networked;

            function fetchedFromNetwork(response) {
                var cacheCopy = response.clone();
                caches.open('agents-offline')
                    .then(function add(cache) {
                        var accepts = event.request.headers.get('Accept'),
                        html = accepts.indexOf('text/html,application/xhtml+xml');

                        if(event.request && html === -1){
                            cache.put(event.request, cacheCopy);
                        }
                    });
                return response;
            }

            function unableToResolve () {
                return caches.open('agents-offline').then(function(cache) {
                    return cache.match('offline.html');
                });
            }
        }));
});

// Event to update the offline page
self.addEventListener('refreshOffline', function(response) {
    return caches.open('agents-offline').then(function(cache) {
        return cache.put(offlineSite, response);
    });
});