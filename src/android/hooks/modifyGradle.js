const fs = require('fs');
const path = require('path');

const gradleFilePath = path.join('platforms', 'android', 'app', 'build.gradle');

fs.readFile(gradleFilePath, 'utf8', function (err, data) {
    if (err) {
        return console.log(err);
    }

    let modifiedData = data.replace(/apply plugin: 'kotlin-android-extensions'/g, '// apply plugin: \'kotlin-android-extensions\'\napply plugin: \'kotlin-parcelize\'');

    fs.writeFile(gradleFilePath, modifiedData, 'utf8', function (err) {
        if (err) return console.log("❌ -- Error file path: "+err);
    });
});