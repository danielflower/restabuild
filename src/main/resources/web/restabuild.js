(function () {
    var $ = document.querySelector.bind(document);

    window.addEventListener('pageshow', function (e) {
        if (e.persisted) {
            setTimeout(function () {
                var submitButton = $('#submitButton');
                submitButton.removeAttribute('disabled');
                if ('fetch' in window) {
                    var ul = $('#recentBuilds');
                    while (ul.firstChild) ul.removeChild(ul.firstChild);
                    loadBuilds(null, 0);
                }
            }, 0);
        }
    });

    document.addEventListener('DOMContentLoaded', function () {
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
            cc.textContent = 'curl -LNs -F \'gitUrl=' + (value || 'git-url') + '\' ' + '-F \'branch=' + (branch || 'master') + '\' ' + (buildParam ? '-F \'param=' + buildParam + '\' ' : '') + '\'' + form.action + '\'';
            history.replaceState(null, null, value ? encodeURI(path) + '?url=' + encodeURIComponent(value) : encodeURI(path));
        };
        urlBox.addEventListener('input', update);
        branchBox.addEventListener('input', update);
        buildParamBox.addEventListener('input', update);
        update();

        form.addEventListener('submit', function () {
            $('#submitButton').setAttribute('disabled', 'disabled');
        });

        if ('fetch' in window) {
            loadBuilds(null, 0);
        }
    });

    function loadBuilds(limit, skip) {
        limit = limit || 10;
        var ul = $('#recentBuilds');
        var historyButtons = $('.historyButtons');
        var olderBuildsButton = $('#olderBuildsButton');

        var el = function (tag, content) {
            var e = document.createElement(tag);
            content && (e.textContent = content);
            return e;
        };
        var buildsUrl = 'api/v1/builds?limit=' + limit + '&skip=' + skip + '&timestamp=' + Date.now();
        fetch(buildsUrl)
            .then(function (r) {
                if (!r.ok) throw 'Got ' + r.status + ' from ' + buildsUrl;
                return r.json();
            })
            .then(function (r) {
                var builds = r.builds;
                for (var i = 0; i < builds.length; i++) {
                    var b = builds[i];
                    var li = ul.appendChild(el('li'));
                    li.setAttribute('rb-id', b.id);
                    var queueDate = new Date(Date.parse(b.queuedAt));
                    var a = li.appendChild(el('a', queueDate.toLocaleString()));
                    a.href = b.logUrl;
                    var friendly = b.gitUrl.replace(/\/$/, '');
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
                            li.appendChild(document.createTextNode(' '));
                        }
                    }

                    if (b.cancelUrl) {
                        var cancelForm = li.appendChild(el('form'));
                        cancelForm.method = 'post';
                        cancelForm.action = b.cancelUrl;
                        var cancelButton = cancelForm.appendChild(el('button'));
                        cancelButton.appendChild(document.createTextNode('Cancel'));
                    }
                }

                var hasOlder = builds.length === limit;
                if (!hasOlder) {
                    historyButtons.style.display = 'none';
                } else {
                    olderBuildsButton.onclick = function () {
                        loadBuilds(limit, skip + limit)
                    };
                    historyButtons.style.display = 'block';
                    olderBuildsButton.style.display = hasOlder ? 'inline' : 'none';
                }
            })
            .catch(function (err) {
                console.log('Error looking up builds', err);
            });

    }
})();