const fs = require('fs');
const path = require('path');

const jsonFilePath = path.join('platforms', 'android', 'cdv-gradle-config.json');

fs.readFile(jsonFilePath, 'utf8', function (err, data) {
    if (err) {
        console.error('❌ -- Error reading JSON file:', err);
        return;
    }

    let jsonContent = JSON.parse(data);
    jsonContent.KOTLIN_VERSION = '1.9.20'; // Set the new Kotlin version

    fs.writeFile(jsonFilePath, JSON.stringify(jsonContent, null, 2), 'utf8', function (err) {
        if (err) {
            console.error('Error writing JSON file:', err);
            return;
        }
        console.log('✅ -- KOTLIN_VERSION has been updated to 1.9.20');
    });
});