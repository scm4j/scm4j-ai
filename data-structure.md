# product-list.json

Is represented by `product-list.json` file inside `product artifact`

```json

{
  "repositories": [
        "https://dev.untill.com/artifactory/repo",
	"http://central.maven.org/maven2",
	"https://jitpack.io"
  ],
  "products": {
 	"art1": {
	  "artifactId": "scm4j:art-id1",
	  "appliedVersionsUrl": "http://example.com",
	  "hidden": false
	},
	"art2": {
	  "artifactId": "scm4j:art-id2",
	  "appliedVersionsUrl": "",
	  "hidden": true
  	}
}
  
 ```

# product-versions.json

- cache of repository content
- located in `working folder`

# deployed-products.json

- located in `working folder`

