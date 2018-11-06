const fs = require('fs')
const path = require('path')

// from https://gist.github.com/lovasoa/8691344
function walk(dir) {
  return new Promise((resolve, reject) => {
    fs.readdir(dir, (error, files) => {
      if (error) {
        return reject(error)
      }
      Promise.all(files.map((file) => {
        return new Promise((resolve, reject) => {
          const filepath = path.join(dir, file)
          fs.stat(filepath, (error, stats) => {
            if (error) {
              return reject(error)
            }
            if (stats.isDirectory()) {
              walk(filepath).then(resolve)
            } else if (stats.isFile()) {
              resolve(filepath)
            }
          });
        });
      }))
      .then((foldersContents) => {
        resolve(foldersContents.reduce((all, folderContents) => all.concat(folderContents), []));
      });
    });
  });
}


async function generateList () {
  const files = await walk(path.join(__dirname, 'fonts'))
  for (const filePath of files) {
    let relativePath = filePath.replace(__dirname, '')
    let includeName = `IDR_BRAVE_COMMON${relativePath.replace(/[\/\.\-]/g, '_').toUpperCase()}`
    let gzip = false
    if (path.extname(filePath) === '.css') {
      gzip = true
    }
    // const fileName = path.basename(filePath, '.woff2')
    const includeElementRaw = `<include name="${includeName}" file="brave${relativePath}" type="BINDATA" ${gzip ? 'compress="gzip" ' : ''}/>`
    console.log(includeElementRaw)
  }
}

generateList()
.catch(err => {
  console.error(err)
  process.exit(1)
})