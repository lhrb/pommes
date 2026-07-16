(ns dev.lhrb.pommes.pommes-connection.views.layout
  (:require
   [hiccup2.core :as h]))

(defn panel
  ([content] (panel nil content))
  ([class content]
   (into [:section {:class (str "panel" (when class (str " " class)))}] content)))

(defn button [label]
  [:button {:class "button" :type "submit"} label])

(defn badge [label]
  [:span {:class "badge"} label])

(defn notice [kind content]
  [:div {:class (str "notice notice--" kind) :role (if (= kind "error") "alert" "status")}
   content])

(defn offer-card [content]
  (into [:article.offer-card] content))

(defn modal [content]
  (into [:div {:class "modal" :role "alert"}] content))

(defn stat-row [label value]
  [:div.stat-row [:span label] [:strong value]])

(defn meter [value]
  [:div.meter {:aria-hidden "true"}
   [:span {:style (str "width:" (max 8 (min 100 (long value))) "%")}]] )

(defn page
  [title content]
  (str
   (h/html
    [:html {:lang "de"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css2?family=Press+Start+2P&display=swap"}]
      [:title title]
      [:style "*{box-sizing:border-box} :root{--paper:#fff4d6;--ink:#332016;--yellow:#f7c843;--red:#b8372f;--sage:#75806b;--line:2px solid var(--ink)} body{margin:0;border-top:8px solid var(--red);background:var(--paper);color:var(--ink);font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.45} main{width:min(100%,680px);margin:0 auto;padding:24px 16px 40px} h1,h2,h3,.pixel{font-family:'Press Start 2P','Courier New',monospace;line-height:1.35;letter-spacing:-.04em} h1{font-size:20px;margin:0} h2{font-size:13px;margin:0 0 16px} h3{font-size:11px;margin:0 0 8px} p{margin:0 0 16px} a{color:var(--ink);font-weight:700;text-decoration-thickness:2px;text-underline-offset:3px} .masthead{border-bottom:var(--line);padding-bottom:16px;margin-bottom:16px}.eyebrow{font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;margin:0 0 8px}.subhead{margin:8px 0 0}.panel{border:var(--line);background:#fff9e9;padding:16px;margin:0 0 16px}.panel--yellow{background:var(--yellow)}.panel--quiet{background:#f2ead1}.panel--tight{padding:12px}.badge{display:inline-block;border:1px solid var(--ink);background:var(--yellow);padding:4px 8px;font-size:12px;font-weight:700;line-height:1.2;text-transform:uppercase}.notice,.modal{border:var(--line);padding:12px 16px;margin:0 0 16px;font-weight:700}.notice--good{background:#dce3d2}.notice--warning{background:var(--yellow)}.notice--error,.modal{background:#f6d3ce}.notice--quiet{background:#ede5ce}.stat-row{display:flex;align-items:baseline;justify-content:space-between;gap:16px;border-bottom:1px solid var(--ink);padding:8px 0}.stat-row:first-child{padding-top:0}.stat-row:last-child{border-bottom:0;padding-bottom:0}.stat-row strong{font-size:20px;white-space:nowrap}.grid{display:grid;gap:16px}.offer-card{border:var(--line);padding:12px;margin:0 0 12px;background:var(--paper)}.offer-card__top{display:flex;justify-content:space-between;gap:8px;align-items:flex-start}.meter{height:12px;border:1px solid var(--ink);background:#fff9e9;margin-top:8px}.meter span{display:block;height:100%;background:var(--sage)}.meta{font-size:13px}.numeric{text-align:right}.forecast-cards{display:grid;gap:12px}.forecast-table{display:none}.button,button{display:block;min-height:44px;width:100%;border:var(--line);border-radius:0;background:var(--red);color:#fff9e9;font:700 16px/1.2 Arial,Helvetica,sans-serif;padding:10px 16px;cursor:pointer}.button:hover,button:hover{background:var(--ink)} form{margin:0}fieldset{border:var(--line);padding:12px;margin:0 0 16px;background:#fff9e9}legend{padding:0 4px;font-weight:700}label{display:block;font-weight:700;margin:0 0 12px}input,select{display:block;width:100%;min-height:44px;margin-top:4px;border:1px solid var(--ink);border-radius:0;background:#fffdf4;color:var(--ink);font:16px Arial,Helvetica,sans-serif;padding:8px}input:focus,select:focus,button:focus,a:focus{outline:3px solid var(--yellow);outline-offset:2px}.errors{border:var(--line);background:#f6d3ce;margin:0 0 16px;padding:12px 12px 12px 32px}.errors li+li{margin-top:4px}table{width:100%;border-collapse:collapse;font-size:14px}th,td{border-bottom:1px solid var(--ink);padding:8px 4px;text-align:left}th{font-size:12px;text-transform:uppercase}td:first-child,th:first-child{padding-left:0}td:last-child,th:last-child{padding-right:0}.invite{overflow-wrap:anywhere}@media(min-width:560px){main{padding:32px 24px 48px}.grid--two{grid-template-columns:repeat(2,minmax(0,1fr))}.grid--three{grid-template-columns:repeat(3,minmax(0,1fr))}.forecast-cards{display:none}.forecast-table{display:table}.button,button{width:auto;min-width:220px}}"]]
     [:body content]])))

(comment
  (page "Pommes Connection" [:main [:h1 "Hello"]]))
