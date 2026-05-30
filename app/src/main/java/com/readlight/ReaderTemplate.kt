package com.readlight

object ReaderTemplate {

    fun build(
        bodyHtml: String,
        alignmentCss: String,
        goToLastPage: Boolean,
        targetPage: Int,
        targetAnchor: String
    ): String {
        val escapedAnchor = targetAnchor
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        return """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <style>
        * {
            margin: 0 !important;
            padding: 0;
            box-sizing: border-box;
            -webkit-user-select: none;
            user-select: none;
            -webkit-touch-callout: none;
            -webkit-tap-highlight-color: transparent;
            break-inside: auto !important;
            page-break-inside: auto !important;
        }
        html, body {
            height: 100%;
            overflow: hidden;
            background: #000000;
        }
        #reader {
            height: 100%;
            overflow: hidden;
        }
        #book {
            height: 100%;
            column-fill: auto;
            background: #000000;
            color: #FFFFFF;
            font-family: sans-serif;
            font-size: 17px;
            line-height: 1.7;
            padding: 0;
            opacity: 0;
        }
        #book.ready { opacity: 1; }
        .page-padding {
            padding-left: 8px !important;
            padding-right: 8px !important;
        }
        h1, h2, h3, h4, h5, h6 {
            font-size: 17px !important;
            line-height: 1.7;
            font-weight: normal;
            color: #FFFFFF;
            text-align: center;
            text-indent: 0;
            margin-top: 1.7em !important;
            margin-bottom: 1.7em !important;
        }
        h1:first-child, h2:first-child, h3:first-child,
        h4:first-child, h5:first-child, h6:first-child {
            margin-top: 0 !important;
        }
        a { color: #FFFFFF; text-decoration: none; }
        img {
            max-width: 100%;
            max-height: 100vh;
            height: auto;
            display: block !important;
            float: none !important;
            margin: 0 auto !important;
            pointer-events: none;
        }
        img[class*="pic-s"],
        img[class*="pic-inline"],
        img[class*="tpzz"],
        img[class*="inline1"] {
            display: inline !important;
            height: 1em !important;
            width: auto !important;
            vertical-align: -0.15em !important;
            margin: 0 !important;
        }
        p {
            margin-top: 0 !important;
            margin-bottom: 0 !important;
            text-indent: 1em;
            text-align: justify;
            orphans: 1;
            widows: 1;
        }
        p:first-child,
        h1 + p, h2 + p, h3 + p,
        h4 + p, h5 + p, h6 + p {
            text-indent: 0;
        }
        div:empty, figure:empty { display: none !important; }
        blockquote,
        .epigraph, .epi, .epigraphe,
        .quote, .blockquote, .quoted,
        .verse, .poem, .poetry, .stanza,
        .pullquote, .pull-quote,
        .extract, .excerpt,
        .block,
        [class^="kt"], [class*=" kt"] {
            margin-left: 2em !important;
            margin-right: 2em !important;
            font-style: italic;
        }
        blockquote p,
        .epigraph p, .epi p, .epigraphe p,
        .quote p, .blockquote p, .quoted p,
        .verse p, .poem p, .poetry p, .stanza p,
        .pullquote p, .pull-quote p,
        .extract p, .excerpt p,
        .block p,
        [class^="kt"] p, [class*=" kt"] p {
            text-indent: 0 !important;
            text-align: left !important;
        }
        hr {
            border: none;
            border-top: 1px solid #FFFFFF;
            margin-top: 0 !important;
            margin-bottom: 0 !important;
            height: 0;
            break-before: avoid;
            break-after: avoid;
        }
        sup, sub {
            font-size: 0.75em;
            line-height: 0;
            position: relative;
            vertical-align: baseline;
        }
        sup { top: -0.4em; }
        sub { bottom: -0.3em; }
        a sup { text-decoration: underline; }
        .center, .centered, .tc, .chapter-title, .book-title {
            text-align: center !important;
        }
        .subtitle, .sub-title, .section-title, .part-title,
        .title1, .title2, .title3,
        .chapter-title, .chapter-name {
            text-align: center !important;
            text-indent: 0 !important;
            margin-top: 1.7em !important;
            margin-bottom: 1.7em !important;
        }
        .subtitle:first-child, .sub-title:first-child,
        .title1:first-child, .title2:first-child, .title3:first-child,
        .chapter-title:first-child, .chapter-name:first-child {
            margin-top: 0 !important;
        }
        [align="right"],
        .right, .align-right, .text-right,
        .author, .signature, .attribution, .date, .credit {
            text-align: right !important;
        }
        $alignmentCss
        .image-btn {
            display: block;
            text-align: center !important;
            text-indent: 0 !important;
            color: #FFFFFF;
            font-size: 17px;
            line-height: 1.7;
            margin-top: 1.7em !important;
            margin-bottom: 1.7em !important;
            cursor: default;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            padding-left: 24px !important;
            padding-right: 24px !important;
        }
        .image-btn:first-child {
            margin-top: 0 !important;
        }
        .image-icon {
            width: 1em;
            height: 1em;
            vertical-align: -0.15em;
            display: inline-block;
            margin-right: 0.4em !important;
        }
        #imageOverlay {
            display: none;
            position: fixed;
            top: 0; left: 0;
            width: 100%; height: 100%;
            background: #000000;
            z-index: 1000;
        }
        #imageOverlay.visible { display: block; }
        #imageOverlayContent {
            position: absolute;
            top: 0; left: 0; right: 0;
            bottom: 50px;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: flex-start;
            padding: 0;
            overflow-y: auto;
            gap: 16px;
        }
        .overlay-img {
            max-width: 100% !important;
            width: 100% !important;
            height: auto !important;
            object-fit: contain !important;
            display: block !important;
            flex-shrink: 0;
        }
        #imageCaption {
            color: #FFFFFF;
            font-family: sans-serif;
            font-size: 17px;
            line-height: 1.7;
            text-align: center;
            padding: 8px 16px 0 16px;
            width: 100%;
        }
        #imageCloseBar {
            position: absolute;
            bottom: 0; left: 0; right: 0;
            height: 50px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #imageClose {
            color: #FFFFFF;
            font-family: sans-serif;
            font-size: 30px;
            line-height: 1;
            padding: 10px 24px;
            cursor: default;
        }
        #footnoteOverlay {
            display: none;
            position: fixed;
            top: 0; left: 0;
            width: 100%; height: 100%;
            background: #000000;
            z-index: 1000;
            flex-direction: column;
            align-items: center;
            justify-content: space-between;
        }
        #footnoteOverlay.visible { display: flex; }
        #footnoteContent {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #FFFFFF;
            font-family: sans-serif;
            font-size: 22px;
            line-height: 1.7;
            text-align: center;
            word-wrap: break-word;
            padding: 48px 32px 0 32px;
            width: 100%;
        }
        #footnoteCloseBar {
            width: 100%;
            height: 50px;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }
        #footnoteClose {
            color: #FFFFFF;
            font-family: sans-serif;
            font-size: 30px;
            line-height: 1;
            padding: 10px 24px;
            cursor: default;
        }
    </style>
    <script>
        var pageWidth = 0;
        var totalPages = 1;
        var goToLastPage = $goToLastPage;
        var targetPage = $targetPage;
        var targetAnchor = '$escapedAnchor';
        var currentTranslateX = 0;

        function replaceImagesWithButtons() {
            var seenSrcs = new Set();
            var book = document.getElementById('book');

            function getReplaceTarget(img) {
                var target = img;
                var p = img.parentElement;
                while (p && p !== book && p.children.length === 1) {
                    target = p;
                    p = p.parentElement;
                }
                return target;
            }

            function makeImageButton(srcs, captionText, captionEl) {
                var btn = document.createElement('div');
                btn.className = 'image-btn';
                btn.setAttribute('data-srcs', JSON.stringify(srcs));
                btn.setAttribute('data-caption', captionText);
                var iconSvg =
                    '<svg class="image-icon" viewBox="0 0 24 24" fill="none" ' +
                    'stroke="currentColor" stroke-width="1.5" ' +
                    'xmlns="http://www.w3.org/2000/svg">' +
                    '<rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>' +
                    '<circle cx="8.5" cy="8.5" r="1.5" fill="currentColor" stroke="none"/>' +
                    '<path d="M21 15l-5-5L5 21"/>' +
                    '</svg>';
                btn.innerHTML = '<span style="text-decoration:underline;text-underline-offset:3px;display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom;box-sizing:border-box">' +
                    iconSvg + (captionText || 'Image') + '</span>';
                btn.addEventListener('click', function() {
                    showImageOverlay(
                        JSON.parse(this.getAttribute('data-srcs')),
                        this.getAttribute('data-caption')
                    );
                });
                return btn;
            }

            if (book.textContent.trim().length === 0) return;

            var handledImgs = new Set();

            var candidates = Array.from(book.querySelectorAll('img')).filter(function(img) {
                if (!img.getAttribute('src')) return false;
                if (img.getAttribute('epub:type') === 'cover') return false;
                if (img.getAttribute('role') === 'doc-cover') return false;
                if (img.closest('a')) return false;
                var imgClass = img.getAttribute('class') || '';
                if (imgClass.includes('pic-s') ||
                    imgClass.includes('pic-inline') ||
                    imgClass.includes('tpzz') ||
                    imgClass.includes('inline1')) return false;
                var h = parseInt(img.getAttribute('height') || '0');
                if (h > 0 && h < 50) return false;
                return true;
            });

            var i = 0;
            while (i < candidates.length) {
                var img = candidates[i];
                var target = getReplaceTarget(img);
                var group = [img];
                var groupTargets = [target];
                var j = i + 1;

                while (j < candidates.length) {
                    var nextImg = candidates[j];
                    var nextTarget = getReplaceTarget(nextImg);
                    if (nextTarget.parentElement === target.parentElement &&
                        nextTarget.previousElementSibling === groupTargets[groupTargets.length - 1]) {
                        group.push(nextImg);
                        groupTargets.push(nextTarget);
                        j++;
                    } else {
                        break;
                    }
                }

                if (group.length >= 2) {
                    var lastTarget = groupTargets[groupTargets.length - 1];
                    var captionEl = null;
                    var captionText = '';
                    var nextSib = lastTarget.nextElementSibling;
                    if (nextSib &&
                        !nextSib.querySelector('img') &&
                        nextSib.textContent.trim().length > 0 &&
                        nextSib.textContent.trim().length < 500) {
                        captionText = nextSib.textContent.trim();
                        captionEl = nextSib;
                    }

                    var srcs = group.map(function(img) {
                        return img.getAttribute('src') || '';
                    }).filter(function(src) { return src; });

                    srcs.forEach(function(src) { seenSrcs.add(src); });
                    group.forEach(function(img) { handledImgs.add(img); });

                    var btn = makeImageButton(srcs, captionText, captionEl);
                    groupTargets[0].parentNode.insertBefore(btn, groupTargets[0]);
                    groupTargets.forEach(function(t) {
                        if (t.parentNode) t.parentNode.removeChild(t);
                    });
                    if (captionEl && captionEl.parentNode) {
                        captionEl.parentNode.removeChild(captionEl);
                    }
                }

                i = j > i + 1 ? j : i + 1;
            }

            document.querySelectorAll('#book img').forEach(function(img) {
                if (handledImgs.has(img)) return;

                var src = img.getAttribute('src') || '';
                if (!src) return;

                var imgClass = img.getAttribute('class') || '';
                var isInlineChar = imgClass.includes('pic-s') ||
                                   imgClass.includes('pic-inline') ||
                                   imgClass.includes('tpzz') ||
                                   imgClass.includes('inline1');

                if (!isInlineChar) {
                    if (seenSrcs.has(src)) {
                        img.parentNode && img.parentNode.removeChild(img);
                        return;
                    }
                    seenSrcs.add(src);
                }

                if (img.getAttribute('epub:type') === 'cover' ||
                    img.getAttribute('role') === 'doc-cover') return;

                var el = img.parentElement;
                while (el && el !== book) {
                    var epubType = (el.getAttribute('epub:type') || '').toLowerCase();
                    if (epubType.includes('cover') ||
                        epubType.includes('titlepage') ||
                        epubType.includes('frontmatter')) return;
                    el = el.parentElement;
                }

                if (img.closest('a')) return;

                var par = img.parentElement;
                if (par) {
                    var hasTextSibling = false;
                    par.childNodes.forEach(function(node) {
                        if (node === img) return;
                        if (node.nodeType === 3 && node.textContent.trim()) hasTextSibling = true;
                        if (node.nodeType === 1 && node.textContent.trim()) hasTextSibling = true;
                    });
                    if (hasTextSibling) return;
                }
                var w = parseInt(img.getAttribute('width')  || '0');
                var h = parseInt(img.getAttribute('height') || '0');
                if (h === 0) {
                    try {
                        var imgData = img.getAttribute('data-cf8d25c863d53b2c0c305b8e-img-data') ||
                                      img.getAttribute('data-img-data') || '';
                        if (imgData) {
                            var parsed = JSON.parse(imgData.replace(/&quot;/g, '"'));
                            if (parsed.height) h = parsed.height;
                        }
                    } catch(e) {}
                }
                if ((w > 0 && w < 50) || (h > 0 && h < 50)) return;

                var rawAlt = img.getAttribute('alt') || '';
                var captionText = ['alt', 'image', 'img'].indexOf(rawAlt.toLowerCase()) >= 0 ? '' : rawAlt;
                var captionEl = null;
                var figure = img.closest('figure');
                if (figure) {
                    var fc = figure.querySelector('figcaption');
                    if (fc) { captionText = fc.textContent.trim(); captionEl = fc; }
                }
                if (!captionEl) {
                    var next = img.nextElementSibling;
                    if (!next && img.parentElement) next = img.parentElement.nextElementSibling;
                    if (next) {
                        var nextClass = (next.getAttribute('class') || '').toLowerCase();
                        var isContentParagraph = nextClass.includes('normal') ||
                                                 nextClass.includes('bodytext') ||
                                                 nextClass.includes('body') ||
                                                 nextClass.includes('content');
                        var t = next.textContent.trim();
                        if (!isContentParagraph && t && t.length < 300) {
                            captionText = t;
                            captionEl = next;
                        }
                    }
                }

                var replaceTarget = getReplaceTarget(img);
                var btn = makeImageButton([src], captionText, captionEl);
                replaceTarget.parentNode.insertBefore(btn, replaceTarget);
                replaceTarget.parentNode.removeChild(replaceTarget);
                if (captionEl && captionEl.parentNode && captionEl !== replaceTarget) {
                    captionEl.parentNode.removeChild(captionEl);
                }
            });
        }

        function showImageOverlay(srcs, caption) {
            var content = document.getElementById('imageOverlayContent');
            content.querySelectorAll('.overlay-img').forEach(function(e) { e.remove(); });

            var srcList = Array.isArray(srcs) ? srcs : [srcs];
            var cap = document.getElementById('imageCaption');

            srcList.forEach(function(src) {
                var img = document.createElement('img');
                img.className = 'overlay-img';
                img.src = src;
                content.insertBefore(img, cap);
            });

            cap.textContent = caption || '';
            cap.style.display = caption ? 'block' : 'none';
            document.getElementById('imageOverlay').classList.add('visible');
            Android.onImageOverlayShown();
        }

        function hideImageOverlay() {
            document.getElementById('imageOverlay').classList.remove('visible');
            document.querySelectorAll('.overlay-img').forEach(function(e) { e.remove(); });
            Android.onImageOverlayDismissed();
        }

        function normalizeFootnoteLinks() {
            document.querySelectorAll('a').forEach(function(a) {
                var isFootnote = false;
                for (var i = 0; i < a.attributes.length; i++) {
                    if (!a.attributes[i].name.startsWith('data-')) continue;
                    try {
                        var json = JSON.parse(a.attributes[i].value);
                        if (json.name && json.frag) { isFootnote = true; break; }
                    } catch(ex) {}
                }
                var href = a.getAttribute('href') || '';
                if (href.includes('#') && !href.startsWith('javascript')) isFootnote = true;
                if (!isFootnote) return;
                (function stripBrackets(node) {
                    node.childNodes.forEach(function(child) {
                        if (child.nodeType === 3) {
                            child.textContent = child.textContent
                                .replace(/[\[\]()（）【】]/g, '').trim();
                        } else {
                            stripBrackets(child);
                        }
                    });
                })(a);
            });
        }

        function init() {
            var probe = document.createElement('span');
            probe.style.cssText = 'font-size:17px;line-height:1.7;position:absolute;visibility:hidden';
            probe.textContent = 'X';
            document.body.appendChild(probe);
            var lineH = probe.getBoundingClientRect().height;
            document.body.removeChild(probe);
            if (lineH < 1) lineH = 28.9;

            var totalH = window.innerHeight;
            var lines = Math.floor(totalH / lineH);
            var vPad = Math.floor((totalH - lines * lineH) / 2);

            var book = document.getElementById('book');
            book.style.paddingTop    = vPad + 'px';
            book.style.paddingBottom = vPad + 'px';

            pageWidth = window.innerWidth;
            book.style.columnWidth = pageWidth + 'px';
            book.style.columnGap   = '0px';
        }

        window.onload = function() {
            replaceImagesWithButtons();
            normalizeFootnoteLinks();
            init();
            document.querySelectorAll('#book svg').forEach(function(svg) {
                svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
            });
        };

        function getPageCount() {
            var book = document.getElementById('book');
            pageWidth  = window.innerWidth;
            totalPages = Math.max(1, Math.round(book.scrollWidth / pageWidth));

            if (goToLastPage && totalPages > 1) {
                currentTranslateX = -(totalPages - 1) * pageWidth;
                book.style.transform = 'translateX(' + currentTranslateX + 'px)';
                Android.onGoToLastPage(totalPages - 1, totalPages);
            } else if (targetAnchor) {
                var el = document.getElementById(targetAnchor);
                if (el) {
                    var elLeft = el.getBoundingClientRect().left;
                    var elPage = Math.max(0, Math.min(
                        Math.floor(elLeft / pageWidth), totalPages - 1
                    ));
                    currentTranslateX = -elPage * pageWidth;
                    book.style.transform = 'translateX(' + currentTranslateX + 'px)';
                    Android.onGoToLastPage(elPage, totalPages);
                }
            } else if (targetPage > 0) {
                var safePage = Math.min(targetPage, totalPages - 1);
                currentTranslateX = -safePage * pageWidth;
                book.style.transform = 'translateX(' + currentTranslateX + 'px)';
            }

            book.classList.add('ready');
            return totalPages;
        }

        function goToPage(page) {
            currentTranslateX = -page * pageWidth;
            document.getElementById('book').style.transform =
                'translateX(' + currentTranslateX + 'px)';
        }

        function navigateToAnchor(anchorId) {
            var el = document.getElementById(anchorId);
            if (!el) return;
            var elLayoutLeft = el.getBoundingClientRect().left - currentTranslateX;
            var elPage = Math.max(0, Math.min(
                Math.floor(elLayoutLeft / pageWidth), totalPages - 1
            ));
            goToPage(elPage);
            Android.onAnchorNavigated(elPage);
        }

        function showFootnote(content) {
            document.getElementById('footnoteContent').textContent = content;
            document.getElementById('footnoteOverlay').classList.add('visible');
        }

        function hideFootnote() {
            document.getElementById('footnoteOverlay').classList.remove('visible');
            Android.onFootnoteDismissed();
        }

        document.addEventListener('click', function(e) {
            if (e.target.id === 'imageClose') { hideImageOverlay(); return; }
            if (e.target.id === 'footnoteClose') { hideFootnote(); return; }
            var a = e.target;
            while (a && a.tagName !== 'A') a = a.parentElement;
            if (!a) return;
            for (var i = 0; i < a.attributes.length; i++) {
                if (!a.attributes[i].name.startsWith('data-')) continue;
                try {
                    var json = JSON.parse(a.attributes[i].value);
                    if (json.name && json.frag) {
                        e.preventDefault();
                        Android.onFootnoteLinkClicked(json.frag, json.name);
                        return;
                    }
                } catch(ex) {}
            }
            var href = a.getAttribute('href') || '';
            if (!href || href === 'javascript:void(0)') return;
            if (!href.includes('#')) return;
            e.preventDefault();
            var hashIndex = href.indexOf('#');
            var file     = href.substring(0, hashIndex);
            var fragment = href.substring(hashIndex + 1);
            if (fragment) Android.onFootnoteLinkClicked(fragment, file);
        });

        document.addEventListener('selectstart', function(e) { e.preventDefault(); return false; });
        document.addEventListener('contextmenu', function(e) { e.preventDefault(); return false; });

        window.onresize = function() { init(); getPageCount(); };
    </script>
</head>
<body>
    <div id="reader">
        <div id="book"><div class="page-padding">$bodyHtml</div></div>
    </div>
    <div id="footnoteOverlay">
        <div id="footnoteContent"></div>
        <div id="footnoteCloseBar">
            <div id="footnoteClose">×</div>
        </div>
    </div>
    <div id="imageOverlay">
        <div id="imageOverlayContent">
            <div id="imageCaption"></div>
        </div>
        <div id="imageCloseBar">
            <div id="imageClose">×</div>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
