(ns dev.lhrb.pommes.pommes-connection.views.wiki
  (:require [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
            [ring.util.response :as response]))

(defn page [{:keys [game viewer hx?]}]
  (-> (layout/game-response (str (:name game) " | Budenwissen") game viewer :wiki
                            [[:header.screen-head (layout/icon "nav_wiki" "") [:div [:h1 "Budenwissen"] [:p "Ein Merkblatt für Leute, die aus einer Bude etwas Bleibendes machen wollen."]]]
                             (layout/panel "panel--yellow"
                                           [[:h2 "Die goldene Pommes"]
                                            [:p "Eine Partie dauert sieben Tage, also 20160 Ticks. Wenn die Woche um ist, wird die Bude mit dem meisten Prestige zur Goldenen Pommes erklärt. Das ist eine Auszeichnung und keine Entschuldigung."]
                                            [:p "Geld hält den Laden offen. Prestige entscheidet, wer am Ende als bemerkenswerte Angelegenheit gilt."]])
                             (layout/panel
                              [[:h2 "Dem Markt dienen"]
                               [:p "Jede Kundschaft verlangt etwas anderes. Studierende, Rentner und Feinschmecker kommen nicht aus demselben Grund und sollten darum auch nicht mit derselben Karte behandelt werden."]
                               [:p "Passe Gerichte, Preise und Portionen dem Markt an. Die Lage ändert sich im Lauf der Woche; eine Karte, die gestern vernünftig war, kann heute nur noch ein Gesprächsstoff sein."]
                               [:p "Gute Arbeit bringt Prestige durch:"]
                               [:ul
                                [:li "einen hohen Marktanteil bei einer Kundschaft"]
                                [:li "besonders gefragte Gerichte"]
                                [:li "viele verkaufte Portionen"]
                                [:li "außergewöhnliche Einnahmen"]]
                               [:p "Eine Spezialisierung kann eine Kundschaft fest an die eigene Schlange binden. Eine breite Karte hält besser durch, wenn die Straße ihre Meinung ändert."]])
                             (layout/panel
                              [[:h2 "Einen Namen behalten"]
                               [:p "Stammgäste sind mehr als einzelne Verkäufe mit Gewohnheit. Wer sie anzieht und zufrieden hält, sammelt Prestige und hat einen Laden, der sich nicht bei jedem Wetter neu erfinden muss."]
                               [:p "Buden ohne treue Kundschaft sind stärker von der wechselnden Marktlage abhängig. Das kann gut gehen, wird aber selten als Charakterstärke ausgelegt."]])
                             (layout/panel
                              [[:h2 "Investitionen wählen"]
                               [:p "Forschung verbessert die Bude für den Rest der Partie. Es gibt mehr Möglichkeiten, als in einer Woche ordentlich ausgebaut werden können; man muss also entscheiden, worauf man sich verlassen will."]
                               [:p "Wer einem Forschungsgebiet voraus ist, gewinnt womöglich auch an Ansehen. Der Schwarzmarkt bietet andere Vorteile: ungewöhnliche Kniffe, unversteuerte Einnahmen und Schutz vor manchen Unannehmlichkeiten. Er weckt allerdings Interesse bei Leuten mit Formularen."]
                               [:p "Keine Bude beherrscht alles. Baue die, für die du später geradestehen möchtest."]])
                             (layout/panel
                              [[:h2 "Zur rechten Zeit handeln"]
                               [:p "Du darfst höchstens zwei Aktionen in zwölf Stunden ausführen. Deshalb ist der richtige Zeitpunkt wichtiger als dauernde Betriebsamkeit, auch wenn diese im Imbissgewerbe überzeugend aussehen kann."]
                               [:p "Mit Aktionen kannst du eine Kundschaft anlocken, die örtliche Nachfrage verschieben, eine Gelegenheit vorübergehend verbessern, andere Buden stören oder die führende Bude herausfordern."]
                               [:p "Ein mutiger Schritt gegen starke Konkurrenz bringt mehr Prestige als das Treten gegen einen schwächeren Wagen. Er lohnt sich aber nur, wenn du seinen Vorteil auch nutzen kannst."]])
                             (layout/panel
                              [[:h2 "Die Bücher führen — oder nicht"]
                               [:p "Unversteuerte Einnahmen können den Ausbau beschleunigen. Sie können Forschung, Aktionen und Wachstum bezahlen, bevor jemand nach der Herkunft fragt."]
                               [:p "Bei einer Prüfung verschwinden solche Gewinne womöglich wieder, und der gute Ruf bekommt einen Knick. Die sicherste Bude gewinnt nicht immer. Die gierigste ebenfalls nicht."]])
                             (layout/panel "panel--quiet"
                                           [[:h2 "Den eigenen Weg finden"]
                                            [:p "Es gibt keinen einzigen Weg zur Goldenen Pommes. Du kannst eine wertvolle Kundschaft beherrschen, die Karte mit dem Markt wechseln, Forschung vorantreiben, Stammgäste sammeln, die Stärksten herausfordern, den Schwarzmarkt bemühen oder eine große, wirksame Bude betreiben."]
                                            [:p "Beobachte den Markt, beobachte die anderen Buden und gib dein Geld mit Absicht aus. Am Ende der Woche zählt das Prestige. Die Stadt wird sich dann entscheiden, wer die besten Pommes serviert."]])] hx?)
      response/response (response/content-type "text/html; charset=utf-8")))
