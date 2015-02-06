#!/usr/bin/env node

// Define required.
var fs = require("fs");
var xml2js = require('xml2js');
var parseString = xml2js.parseString;
var builder = new xml2js.Builder();

// Define differents paths.
var pathConfigXml = "config.xml";
var pathAndroidCordova = "platforms/android/";
var pathResAndroidCordova = "platforms/android/res/";
var pathAndroidPlugin = __dirname+"/../../src/android/";
var pathResAndroidPlugin = pathAndroidPlugin+"customCamera/res/";
var pathResPlugin = __dirname+"/../../res/";

/**
 * Generate a path based on a package name.
 * 
 * @param {string} packageName Package name, eg: com.example.
 * 
 * @return {string} Return path generate.
 */
var generatePathFrompackageName = function(packageName) {
    return packageName.split(".").join("/");
}

/**
 * Create different java classes.
 * 
 * @param {string} packageName Package name of the current application.
 */
var createClasses = function(packageName) {
    var pathCameraActivity = pathAndroidPlugin+"customCamera/src/org/geneanet/customcamera/CameraActivity.java";
    var pathCameraLauncher = pathAndroidPlugin+"CameraLauncher.java";

    if (fs.existsSync(pathCameraActivity) && fs.existsSync(pathCameraLauncher)) {
        // Rewrite package of CameraActivity class.
        var contentCameraActivity = fs.readFileSync(pathCameraActivity, {encoding: "utf8"});
        contentCameraActivity = contentCameraActivity.replace(/^package\s[^;]*/,"package "+packageName);
        fs.writeFileSync(pathAndroidCordova+"src/"+generatePathFrompackageName(packageName)+"/CameraActivity.java", contentCameraActivity);

        // Rewrite import of CameraLauncher class.
        var contentCameraLauncher = fs.readFileSync(pathCameraLauncher, {encoding: "utf8"});
        contentCameraLauncher = contentCameraLauncher.replace("XXX_NAME_CURRENT_PACKAGE_XXX", packageName);
        fs.writeFileSync(
            pathAndroidCordova+"src/"+generatePathFrompackageName(contentCameraLauncher.match(/package\s([^;]*)/)[1])+"/CameraLauncher.java",
            contentCameraLauncher
        );
    } else {
        console.error("File CameraActivity.java or/and CameraLauncher.java not found.");
        process.exit(1);
    }
}

/**
 * Update AndroidManifest.xml of the current application.
 * 
 * @param {string} packageName Package name of the current application.
 */
var updateAndroidManifest = function(packageName) {
    var pathAndroidManifestCordova = pathAndroidCordova+"AndroidManifest.xml";
    if (fs.existsSync(pathAndroidManifestCordova)) {
        // get content AndroidManifest.
        var contentAndroidManifest = fs.readFileSync(pathAndroidManifestCordova, {encoding: "utf8"});
        parseString(contentAndroidManifest, function (err, result) {
            contentAndroidManifest = result;
        });

        // add activity if needed.
        var needAddActivity = true;
        var currentActivities = contentAndroidManifest["manifest"]["application"][0]["activity"];
        for (var i = currentActivities.length - 1; i >= 0; i--) {
            if (currentActivities[i]["$"]["android:name"] == "CameraActivity") {
                needAddActivity = false;
            }
        };
        if (needAddActivity) {
            contentAndroidManifest["manifest"]["application"][0]["activity"].push({
                $: {
                    "android:name": "CameraActivity",
                    "android:label": "CameraActivity",
                }
            })
            var newXmlAndroidManifest = builder.buildObject(contentAndroidManifest);
            fs.writeFileSync(
                pathAndroidManifestCordova,
                newXmlAndroidManifest
            );
        }
    } else {
        console.error("File AndroidManifest.xml for cordova not found.");
        process.exit(1);
    }
}

/**
 * Update differents config file (translate, res/layout, etc).
 */
var updateConfig = function() {
    var pathLayoutCordova = pathResAndroidCordova+"layout/";
    var pathLayoutPlugin = pathAndroidPlugin+"customCamera/res/layout/";
    var pathLayoutCameraActivity = pathLayoutPlugin+"activity_camera_view.xml";

    // create directory layout in cordova if it doesn't exist.
    if (!fs.existsSync(pathLayoutCordova)) {
        fs.mkdirSync(pathLayoutCordova);
    }

    // "copy" layout for camera.
    if (fs.existsSync(pathLayoutCameraActivity)) {
        var layoutCameraActivityContent = fs.readFileSync(pathLayoutCameraActivity, {encoding: "utf8"});

        fs.writeFileSync(pathLayoutCordova+"activity_camera_view.xml", layoutCameraActivityContent);
    } else {
        console.error("File activity_camera_view.xml in plugin not found.");
        process.exit(1);
    }

    // add translations.
    var pathTranslations = pathResPlugin+"translations.json";
    if (fs.existsSync(pathTranslations)) {
        // get translations.
        var translationsForApplication = fs.readFileSync(pathTranslations, {encoding: "utf8"});
        translationsForApplication = JSON.parse(translationsForApplication);
        for (lang in translationsForApplication) {
            var pathFileTranslate = pathResAndroidCordova+"values-"+lang+"/";
            if (lang == "0") {
                pathFileTranslate = pathResAndroidCordova+"values/";
            }

            var objToXml;
            // already exist, get data.
            if (fs.existsSync(pathFileTranslate+"strings.xml")) {
                var objToXml = fs.readFileSync(pathFileTranslate+"strings.xml", {encoding: "utf8"});
                parseString(objToXml, function (err, result) {
                    objToXml = result;
                });
            } else {
                // generate minimal object.
                objToXml = {
                    resources: {
                        string: []
                    }
                };
            }

            // add message.
            for (tag in translationsForApplication[lang]) {
                objToXml["resources"]["string"].push({
                    _: translationsForApplication[lang][tag],
                    $: {
                        name: tag
                    }
                });
            }

            var xmlBuild = builder.buildObject(objToXml);
            if (!fs.existsSync(pathFileTranslate)) {
                fs.mkdirSync(pathFileTranslate);
            }
            fs.writeFileSync(pathFileTranslate+"strings.xml", xmlBuild);
        }
    } else {
        console.error("File translations.json in plugin not found.");
        process.exit(1);
    }

    var contentResAndroidPlugin = fs.readdirSync(pathResAndroidPlugin);
    for (var i = contentResAndroidPlugin.length - 1; i >= 0; i--) {
        var nameDirDrawable = contentResAndroidPlugin[i];
        if (nameDirDrawable.match(/^drawable-?.*$/) || nameDirDrawable.match(/^values-?.*$/)) {
            var contentDrawableDir = fs.readdirSync(pathResAndroidPlugin+nameDirDrawable);
            for (var j = contentDrawableDir.length - 1; j >= 0; j--) {
                var nameFileInDrawable = contentDrawableDir[j];
                if (nameFileInDrawable.match(/^strings\.xml$/)) {
                    continue;
                }

                var contentFileDrawable = fs.readFileSync(pathResAndroidPlugin+nameDirDrawable+"/"+nameFileInDrawable);
                if (!fs.existsSync(pathResAndroidCordova+nameDirDrawable)) {
                    fs.mkdirSync(pathResAndroidCordova+nameDirDrawable);
                }
                fs.writeFileSync(pathResAndroidCordova+nameDirDrawable+"/"+nameFileInDrawable, contentFileDrawable);
            };
        }
    };
}

// Check if files required exist.
if (fs.existsSync(pathConfigXml)) {
    // Get the name package of the current application.
    var configContent = fs.readFileSync(pathConfigXml, {encoding: "utf8"});
    parseString(configContent, function (err, result) {
        configContent = result;
    });
    var packageName = configContent["widget"]["$"]["id"];

    createClasses(packageName);
    updateAndroidManifest(packageName);
    updateConfig();
} else {
    console.error("File config.xml for cordova not found.");
    process.exit(1);
}