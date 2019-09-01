document.addEventListener('DOMContentLoaded', function () {
    var $ = document.querySelector.bind(document);
    var cc = $('#curlCommand');
    var form = $('#createForm');
    var urlBox = $('#gitUrlBox');
    var branchBox = $('#branchBox');
    var buildParamBox = $('#buildParamBox');
    var path = location.pathname;
    var apiLink = $('#apiLink');
    apiLink.textContent = apiLink.href;

    if ('URLSearchParams' in window) {
        var qs = new URLSearchParams(location.search);
        urlBox.value = qs.get('url');
        branchBox.value = qs.get('branch') || '';
        buildParamBox.value = qs.get('param') || '';

    }

    var update = function () {
        var value = urlBox.value;
        var branch = branchBox.value;
        var buildParam = buildParamBox.value;
        cc.textContent = 'curl -LNs -F \'gitUrl=' + (value || 'git-url') + '\' '  + '-F \'branch=' + (branch || 'master') + '\' ' + '-F \'param=' + (buildParam || '') + '\' ' + form.action;
        history.replaceState(null, null, value ? encodeURI(path) + '?url=' + encodeURIComponent(value) : encodeURI(path));
    };
    urlBox.addEventListener('input', update);
    branchBox.addEventListener('input', update);
    buildParamBox.addEventListener('input', update);
    update();
    form.addEventListener('submit', function () {
        $('#submitButton').disabled = true;
    });

    if ('fetch' in window) {

        var el = function(tag, content) {
            var e = document.createElement(tag);
            content && (e.textContent = content);
            return e;
        };

        fetch('api/v1/builds?limit=10&skip=0')
            .then(function (r) { return r.json(); })
            .then(function (r) {
                console.log('r', r);
                var ul = $('#recentBuilds');
                var builds = r.builds;
                for (var i = 0; i < builds.length; i++) {
                    var b = builds[i];
                    var li = ul.appendChild(el('li'));

                    var queueDate = new Date(Date.parse(b.queuedAt));
                    var a = li.appendChild(el('a', queueDate.toLocaleString()));
                    a.href = b.url;
                    var friendly = b.gitUrl.replace(/\/$/, '');;
                    var lastSlash = friendly.lastIndexOf('/');
                    if (lastSlash > 0) {
                        friendly = friendly.substring(lastSlash + 1);
                    }
                    if (b.gitBranch !== 'master') {
                        friendly += ' (' + b.gitBranch + ')';
                    }
                    li.appendChild(el('span', ' ' + friendly + ' '));

                    li.appendChild(el('strong', b.status));

                    if (b.tagsCreated && b.tagsCreated.length) {
                        li.appendChild(document.createTextNode(' - created tags '));
                        for (var j = 0; j < b.tagsCreated.length; j++) {
                            li.appendChild(el('code', b.tagsCreated[j]));
                            li.appendChild(document.createTextNode(' ' ));
                        }
                    }

                }
                console.log('r', r.builds.length);
            })
            .catch(function (err) {
                console.log('Error looking up builds', err);
            });
    }


});