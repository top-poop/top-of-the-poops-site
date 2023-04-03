import pup from "puppeteer-core";
import yargs from 'yargs'
import console from 'console';

const stderr = new console.Console(process.stderr);

/*
Use an already-running chrome instance, with debugging enabled, to get pages.
Start chrome with (something like) - /opt/google/chrome/google-chrome --remote-debugging-port=21222
 */

const captureScreenshotsOfElements = async (path, elements) => {
    let i = 0;

    for (const element of elements) {

        const name = await element.evaluate(it => it.id)

        const filepath = `${path}/${name}.png`
        console.log(filepath);

        await element.screenshot({ path: filepath });
        i += 1;
    }
};

async function run(url, path) {

    const browser = await pup.launch({
        executablePath: "/opt/google/chrome/google-chrome",
        defaultViewport: null,
        ignoreDefaultArgs: ['--disable-dev-shm-usage']
    })

    const pages = await browser.pages();

    const page = pages[0];

    try {
        const response = await page.goto(url)

        if (response.status() !== 200) {
            throw Error(`Got status ${response.status()} for ${url}`)
        }

        console.log("Loaded page, waiting for page to 'complete'");

        //available after everything is drawn
        await page.waitForSelector("#complete");

        console.log("Page rendering complete");

        const elements = await page.$$(".twitter-badge");

        await captureScreenshotsOfElements(path, elements);
    } finally {
        // await page.close()
    }
}

async function main(url, path) {
    try {
        await run(url, path);
        process.exit(0);
    } catch (error) {
        stderr.log(error);
        process.exit(1);
    }
}

const args = yargs(process.argv.slice(2));

const argv = args
    .usage("$0 <url> <folder>")
    .demandCommand(1)
    .argv;

const url = argv._[0];
const path = argv._[1];

main(url, path)
