(defproject slackhog "0.1.0-SNAPSHOT"
  :description "Back up slack messages."
  :url "https://github.com/Raynes/slackhog"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.2"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.postgresql/postgresql "9.3-1101-jdbc41"]
                 [cheshire "5.3.1"]
                 [me.raynes/fs "1.4.6"]]
  :deploy-repositories {"releases" "clojars"}
  :profiles {:uberjar {:aot [slackhog.core]
                       :uberjar-name "slackhog.jar"}}
  :main slackhog.core)
