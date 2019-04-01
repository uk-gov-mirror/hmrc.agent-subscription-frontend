// Installation: Store offline page
self.addEventListener('install', function (event) {
    console.log('WORKER: install event in progress.');
    event.waitUntil(
        caches.open('agents-offline').then(function (cache) {
            console.log('[PWA Info] Cached offline page');
            return cache.addAll([
                'manifest.json',
                'offline.html',
                '/template/assets/images/gov.uk_logotype_crown.png',
                '/template/assets/images/open-government-licence_2x.png',
                '/template/assets/stylesheets/images/govuk-crest.png',
                '/template/assets/stylesheets/govuk-template.css',
                '/template/assets/stylesheets/fonts.css',
                '/template/assets/stylesheets/govuk-template-print.css',
                '/agent-subscription/assets/stylesheets/styles.css',
                '/agent-subscription/assets/stylesheets/offline.css',
                '/agent-subscription/assets/javascripts/accessible-autocomplete.min.js',
                '/agent-subscription/assets/javascripts/details.pollyfill.js',
                '/agent-subscription/assets/javascripts/ga-events.js',
                '/agent-subscription/assets/javascripts/scripts.js'
                // 'http://localhost:9032/assets/3.0.2/images/crests/hmrc_crest_18px.png',
                // 'http://localhost:9032/assets/3.0.2/images/icon-pointer.png',
                // 'http://localhost:9032/assets/3.0.2/javascripts/vendor/modernizr.js',
                // 'http://localhost:9032/assets/3.0.2/javascripts/application.min.js',
                // 'http://localhost:9032/assets/3.0.2/stylesheets/application.min.css'
            ]);
        })
            .then(function () {
                console.log('WORKER: install completed');
            })
    );
});

// Serve offline page if the fetch fails
self.addEventListener('fetch', function(event) {
    event.respondWith(
        fetch(event.request).catch(function(error) {
                console.log( '[PWA Info] App offline. Serving stored offline page: ' + error );
                return caches.open('agents-offline').then(function(cache) {
                    return cache.match('offline.html');
                });
            }
        ));
});

// Event to update the offline page
self.addEventListener('refreshOffline', function(response) {
    return caches.open('agents-offline').then(function(cache) {
        console.log('[PWA Info] Offline page updated');
        return cache.put(offlineSite, response);
    });
});