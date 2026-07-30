// Client-side search over search-index.json, across both tracks the index renders: the
// works and the people. Every query token must appear somewhere in an entry's text blob,
// which holds titles, names, aliases, roles, publishers, series, subjects, shelves and
// reading states — so "grossman penguin" narrows, and "lauzon" reaches the translator's
// own page rather than only the book he translated.
(function () {
  var box = document.getElementById('q');
  if (!box) {
    return;
  }
  var count = document.getElementById('count');
  var empty = document.getElementById('empty');
  var index = {};

  var tracks = Array.prototype.map.call(
    document.querySelectorAll('[data-track]'),
    function (section) {
      var rows = Array.prototype.slice.call(section.querySelectorAll('[data-id]'));
      return {
        section: section,
        rows: rows,
        noun: section.dataset.track,
        total: rows.length
      };
    }
  ).filter(function (track) {
    return track.total > 0;
  });

  function fold(text) {
    return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  }

  // "3 works · 1 person" — and the plural is the noun's own, so "people" is not "persons".
  function tally(track, shown) {
    return shown + ' ' + (shown === 1 ? track.section.dataset.one : track.noun);
  }

  function filter() {
    var tokens = fold(box.value).split(/\s+/).filter(Boolean);
    var parts = [];
    var found = 0;

    tracks.forEach(function (track) {
      var shown = 0;
      track.rows.forEach(function (row) {
        var blob = index[row.dataset.id] || '';
        var match = tokens.every(function (token) {
          return blob.indexOf(token) !== -1;
        });
        row.hidden = !match;
        if (match) {
          shown++;
        }
      });
      // A track with nothing in it disappears rather than showing an empty heading.
      track.section.hidden = shown === 0;
      if (shown > 0) {
        parts.push(tally(track, shown));
      }
      found += shown;
    });

    count.textContent = parts.join(' · ');
    empty.hidden = found !== 0;
  }

  fetch('search-index.json')
    .then(function (response) { return response.json(); })
    .then(function (data) {
      data.forEach(function (entry) {
        index[entry.id] = fold(entry.text);
      });
      box.addEventListener('input', filter);
      filter();
    })
    .catch(function () {
      // Opened straight from the filesystem, where fetch is blocked: keep the full list
      // and take the search field away rather than leaving one that does nothing.
      count.textContent = tracks.map(function (track) {
        return tally(track, track.total);
      }).join(' · ');
      box.closest('.find').hidden = true;
    });
})();
